
package com.aws.alphabetuniverse.game.service.impl;

import com.aws.alphabetuniverse.game.dto.CreateGameSessionRequest;
import com.aws.alphabetuniverse.game.dto.GameQuestionResponse;
import com.aws.alphabetuniverse.game.dto.GameSessionResponse;
import com.aws.alphabetuniverse.game.entity.GameSession;
import com.aws.alphabetuniverse.game.enums.GameMode;
import com.aws.alphabetuniverse.game.repository.GameSessionRepository;
import com.aws.alphabetuniverse.game.service.GameSessionService;
import com.aws.alphabetuniverse.learning.entity.UserTopicLearningProgress;
import com.aws.alphabetuniverse.learning.repository.UserTopicLearningProgressRepository;
import com.aws.alphabetuniverse.topic.entity.Topic;
import com.aws.alphabetuniverse.topic.repository.TopicRepository;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.vocabulary.entity.Vocabulary;
import com.aws.alphabetuniverse.vocabulary.repository.VocabularyRepository;
import com.aws.alphabetuniverse.game.entity.GameQuestionSnapshot;
import com.aws.alphabetuniverse.game.repository.GameConfigRepository;
import org.hibernate.exception.ConstraintViolationException;
import com.aws.alphabetuniverse.game.service.CheckpointService;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameSessionServiceImpl implements GameSessionService {

    private static final int WORDS_PER_GAME = 5;
    private static final String IN_PROGRESS = "IN_PROGRESS";

    private final UserRepository userRepository;
    private final TopicRepository topicRepository;
    private final VocabularyRepository vocabularyRepository;
    private final UserTopicLearningProgressRepository progressRepository;
    private final GameSessionRepository gameSessionRepository;
    private final GameConfigRepository gameConfigRepository;
    private final CheckpointService checkpointService;

    @Override
    @Transactional
    public GameSessionResponse createSession(
            Long topicId,
            CreateGameSessionRequest request,
            Jwt jwt
    ) {
        // 1. Validate request và JWT.
        if (request == null || request.mode() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Game mode is required"
            );
        }

        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        // 2. Xác định user từ Cognito sub.
        User authenticatedUser = userRepository
                .findByCognitoSub(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"
                ));

        User user = userRepository
                .findByIdForUpdate(authenticatedUser.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"
                ));

        // 3. Kiểm tra Topic.
        Topic topic = topicRepository
                .findByIdAndActiveTrue(topicId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Topic not found"
                ));

        // 4. Lấy toàn bộ từ vựng đang hoạt động.
        List<Vocabulary> activeVocabularies =
                vocabularyRepository
                        .findByTopic_IdAndActiveTrueOrderByOrderNoAscIdAsc(
                                topicId
                        );

        if (activeVocabularies.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Topic has no active vocabularies"
            );
        }

        // 5. Không tạo thêm phiên khi phiên trước vẫn đang chạy.
        if (gameSessionRepository.existsByUser_IdAndTopic_IdAndStatus(
                user.getId(),
                topicId,
                IN_PROGRESS
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An active game session already exists for this topic"
            );
        }

        GameMode mode = request.mode();
        List<Vocabulary> questionVocabularies;
        int batchStartPosition;
        int batchEndPosition;

        // 6. Xử lý theo chế độ.
        switch (mode) {
            case FREE_PLAY -> {
                // Không yêu cầu học trước.
                // Lấy toàn bộ từ và xáo trộn.
                questionVocabularies =
                        new ArrayList<>(activeVocabularies);

                Collections.shuffle(questionVocabularies);

                batchStartPosition = 1;
                batchEndPosition = questionVocabularies.size();
            }


            case CHECKPOINT -> {
                UserTopicLearningProgress progress =
                        requireLearningProgress(user.getId(), topicId);

                int furthestPosition = getFurthestPosition(
                        progress,
                        topicId,
                        activeVocabularies
                );

                int selectedStart = -1;
                int selectedEnd = -1;

                // Chọn checkpoint chưa vượt qua sớm nhất
                // trong các nhóm đã học đủ.
                for (int start = 0;
                     start < activeVocabularies.size();
                     start += WORDS_PER_GAME) {

                    int end = Math.min(
                            start + WORDS_PER_GAME,
                            activeVocabularies.size()
                    );

                    // Chưa học đủ nhóm này thì dừng.
                    if (end > furthestPosition) {
                        break;
                    }

                    if (!checkpointService.hasWonCheckpoint(
                            user.getId(),
                            topicId,
                            start + 1,
                            end,
                            activeVocabularies
                    )) {
                        selectedStart = start;
                        selectedEnd = end;
                        break;
                    }
                }

                // Không có checkpoint đã học đủ nhưng chưa vượt qua.
                if (selectedStart < 0) {
                    int nextStart =
                            (furthestPosition / WORDS_PER_GAME)
                                    * WORDS_PER_GAME;

                    int nextEnd = Math.min(
                            nextStart + WORDS_PER_GAME,
                            activeVocabularies.size()
                    );

                    if (furthestPosition < nextEnd) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Study all vocabularies in this group before starting the checkpoint"
                        );
                    }

                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "All available checkpoints have already been passed"
                    );
                }

                questionVocabularies = new ArrayList<>(
                        activeVocabularies.subList(
                                selectedStart,
                                selectedEnd
                        )
                );

                Collections.shuffle(questionVocabularies);

                batchStartPosition = selectedStart + 1;
                batchEndPosition = selectedEnd;
            }


            case REVIEW -> {
                // Review chỉ được mở khi đã học hết Topic
                // và vượt qua mọi checkpoint.
                UserTopicLearningProgress progress =
                        requireLearningProgress(user.getId(), topicId);

                int furthestPosition = getFurthestPosition(
                        progress,
                        topicId,
                        activeVocabularies
                );

                if (furthestPosition < activeVocabularies.size()) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Complete learning this topic before reviewing"
                    );
                }

                if (!allCheckpointsPassed(
                        user.getId(),
                        topicId,
                        activeVocabularies
                )) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Pass all checkpoints before reviewing"
                    );
                }

                questionVocabularies =
                        new ArrayList<>(activeVocabularies);

                Collections.shuffle(questionVocabularies);

                batchStartPosition = 1;
                batchEndPosition = questionVocabularies.size();
            }

            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported game mode"
            );
        }


        // 7. Lưu snapshot câu hỏi tại thời điểm tạo phiên.

        Map<Long, Integer> topicPositions = new HashMap<>();

        for (int i = 0; i < activeVocabularies.size(); i++) {
            topicPositions.put(
                    activeVocabularies.get(i).getId(),
                    i + 1
            );
        }

        List<GameQuestionSnapshot> questionSnapshots =
                questionVocabularies.stream()
                        .map(vocabulary -> {
                            Integer topicPosition =
                                    topicPositions.get(vocabulary.getId());

                            if (topicPosition == null) {
                                throw new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Vocabulary position not found"
                                );
                            }

                            return new GameQuestionSnapshot(
                                    vocabulary.getId(),
                                    vocabulary.getWord(),
                                    vocabulary.getMeaningVi(),
                                    scrambleLetters(vocabulary.getWord()),
                                    topicPosition
                            );
                        })
                        .toList();

        // Chốt số lượt thử theo cấu hình Admin.
        int maxAttemptsPerWord =
                getRequiredPositiveIntConfig("max_attempt_per_word");

        Instant now = Instant.now();

        GameSession session = new GameSession();
        session.setUser(user);
        session.setTopic(topic);
        session.setMode(mode.name());
        session.setStatus(IN_PROGRESS);
        session.setScore(0);
        session.setWon(false);
        session.setStartedAt(now);
        session.setLastEventAt(now);

        // Lưu vị trí nhóm checkpoint của phiên.
        session.setBatchStartPosition(batchStartPosition);
        session.setBatchEndPosition(batchEndPosition);

        session.setQuestionVocabIds(
                questionVocabularies.stream()
                        .map(Vocabulary::getId)
                        .collect(Collectors.toCollection(ArrayList::new))
        );

        // Hai trường mà GameAttemptServiceImpl cần.
        session.setQuestionSnapshot(questionSnapshots);
        session.setMaxAttemptsPerWord(maxAttemptsPerWord);

        GameSession savedSession;

        try {
            savedSession = gameSessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException ex) {

            if (isActiveSessionUniqueConflict(ex)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "An active game session already exists for this topic",
                        ex
                );
            }

            throw ex;
        }

        // 8. Trả đúng câu hỏi đã lưu trong snapshot.
        List<GameQuestionResponse> questions =
                questionSnapshots.stream()
                        .map(snapshot ->
                                new GameQuestionResponse(
                                        snapshot.vocabularyId(),
                                        snapshot.meaningVi(),
                                        snapshot.scrambledLetters()
                                )
                        )
                        .toList();


        // 9. Trả response.
        return new GameSessionResponse(
                savedSession.getId(),
                topic.getId(),
                topic.getName(),
                mode,
                savedSession.getStatus(),
                batchStartPosition,
                batchEndPosition,
                questions.size(),
                savedSession.getStartedAt(),
                questions
        );
    }

    @Override
    @Transactional
    public void abandonActiveSession(Long topicId, Jwt jwt) {

        // 1. Kiểm tra Topic ID.
        if (topicId == null || topicId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid topic ID"
            );
        }

        // 2. Kiểm tra JWT.
        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        // 3. Xác định người dùng từ Cognito.
        User user = userRepository
                .findByCognitoSub(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"
                ));

        // 4. Tìm và khóa phiên IN_PROGRESS của chính người dùng
        // trong Topic này.
        GameSession session = gameSessionRepository
                .findActiveSessionForUpdate(
                        user.getId(),
                        topicId,
                        IN_PROGRESS
                )
                .orElse(null);

        // 5. Không có phiên đang chơi thì kết thúc an toàn.
        // API có thể được gọi lại mà không gây lỗi.
        if (session == null) {
            return;
        }

        // 6. Đánh dấu phiên đã bị bỏ dở.
        Instant now = Instant.now();

        session.setStatus("ABANDONED");
        session.setWon(false);
        session.setEndedAt(now);
        session.setLastEventAt(now);

        gameSessionRepository.save(session);
    }

    private UserTopicLearningProgress requireLearningProgress(
            Long userId,
            Long topicId
    ) {
        return progressRepository
                .findByUser_IdAndTopic_Id(userId, topicId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Study vocabulary before starting this mode"
                ));
    }

    private int getFurthestPosition(
            UserTopicLearningProgress progress,
            Long topicId,
            List<Vocabulary> activeVocabularies
    ) {
        Vocabulary furthestVocabulary = progress.getFurthestVocab();

        if (furthestVocabulary == null
                || furthestVocabulary.getTopic() == null
                || !topicId.equals(
                furthestVocabulary.getTopic().getId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Learning progress is invalid"
            );
        }

        // Tìm từ đã học xa nhất trong danh sách đang hoạt động.
        for (int i = 0; i < activeVocabularies.size(); i++) {
            if (activeVocabularies.get(i).getId()
                    .equals(furthestVocabulary.getId())) {
                return i + 1;
            }
        }

        // Từ cũ đã bị xóa mềm: tìm từ hoạt động gần nhất
        // có orderNo nhỏ hơn hoặc bằng orderNo cũ.
        Vocabulary replacement =
                findNearestActiveVocabularyAtOrBeforeOrderNo(
                        activeVocabularies,
                        furthestVocabulary.getOrderNo()
                );

        if (replacement == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Unable to repair learning progress"
            );
        }

        progress.setFurthestVocab(replacement);
        progress.setUpdatedAt(Instant.now());
        progressRepository.save(progress);

        for (int i = 0; i < activeVocabularies.size(); i++) {
            if (activeVocabularies.get(i).getId()
                    .equals(replacement.getId())) {
                return i + 1;
            }
        }

        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Unable to resolve active vocabulary position"
        );
    }

    private boolean allCheckpointsPassed(
            Long userId,
            Long topicId,
            List<Vocabulary> activeVocabularies
    ) {
        int total = activeVocabularies.size();

        for (int start = 1;
             start <= total;
             start += WORDS_PER_GAME) {

            int end = Math.min(
                    start + WORDS_PER_GAME - 1,
                    total
            );

            if (!checkpointService.hasWonCheckpoint(
                    userId,
                    topicId,
                    start,
                    end,
                    activeVocabularies
            )) {
                return false;
            }
        }

        return true;
    }

    private List<String> scrambleLetters(String word) {
        if (word == null || word.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Vocabulary has no valid English word"
            );
        }

        List<String> letters = word
                .toUpperCase(Locale.ROOT)
                .chars()
                .mapToObj(c -> String.valueOf((char) c))
                .collect(Collectors.toCollection(ArrayList::new));

        if (letters.size() > 1) {
            String original = String.join("", letters);

            Collections.shuffle(letters);

            if (String.join("", letters).equals(original)) {
                Collections.rotate(letters, 1);
            }
        }

        return List.copyOf(letters);
    }


    private int getRequiredPositiveIntConfig(String key) {
        String value = gameConfigRepository
                .findByConfigKeyAndIsActiveTrue(key)
                .map(config -> config.getConfigValue())
                .filter(configValue ->
                        configValue != null && !configValue.isBlank())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Missing or empty active game configuration: " + key
                ));

        try {
            int result = Integer.parseInt(value.trim());

            if (result < 1) {
                throw new NumberFormatException(
                        "Configuration must be positive"
                );
            }

            return result;

        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Invalid positive integer configuration: " + key
            );
        }
    }

    private boolean isActiveSessionUniqueConflict(Throwable ex) {
        Throwable cause = ex;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation) {
                return "uq_game_sessions_active_user_topic"
                        .equals(violation.getConstraintName());
            }

            cause = cause.getCause();
        }

        return false;
    }

    private Vocabulary findNearestActiveVocabularyAtOrBeforeOrderNo(
            List<Vocabulary> vocabularies,
            Integer targetOrderNo
    ) {
        if (vocabularies == null || vocabularies.isEmpty()) {
            return null;
        }

        if (targetOrderNo == null) {
            return vocabularies.get(0);
        }

        Vocabulary nearest = null;

        for (Vocabulary vocabulary : vocabularies) {
            Integer orderNo = vocabulary.getOrderNo();

            if (orderNo == null || orderNo > targetOrderNo) {
                continue;
            }

            if (nearest == null
                    || orderNo >= nearest.getOrderNo()) {
                nearest = vocabulary;
            }
        }

        return nearest != null
                ? nearest
                : vocabularies.get(0);
    }

}
