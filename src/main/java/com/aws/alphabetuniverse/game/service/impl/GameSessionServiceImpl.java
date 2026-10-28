
package com.aws.alphabetuniverse.game.service.impl;

import com.aws.alphabetuniverse.game.dto.*;
import com.aws.alphabetuniverse.game.entity.GameSession;
import com.aws.alphabetuniverse.game.entity.SessionAttempt;
import com.aws.alphabetuniverse.game.enums.GameMode;
import com.aws.alphabetuniverse.game.repository.GameSessionRepository;
import com.aws.alphabetuniverse.game.repository.SessionAttemptRepository;
import com.aws.alphabetuniverse.game.service.GameSessionProgressResolverService;
import com.aws.alphabetuniverse.game.service.GameSessionService;
import com.aws.alphabetuniverse.game.service.GameUserAuthorizationService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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
    private static final String CHECKPOINT = "CHECKPOINT";

    private final UserRepository userRepository;
    private final TopicRepository topicRepository;
    private final VocabularyRepository vocabularyRepository;
    private final UserTopicLearningProgressRepository progressRepository;
    private final GameSessionRepository gameSessionRepository;
    private final GameConfigRepository gameConfigRepository;
    private final CheckpointService checkpointService;
    private final SessionAttemptRepository sessionAttemptRepository;
    private final GameUserAuthorizationService gameUserAuthorizationService;
    private final GameSessionProgressResolverService progressResolver;

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
        User authenticatedUser =
                gameUserAuthorizationService.findAuthenticatedUser(jwt);

        User user = userRepository
                .findByIdForUpdate(authenticatedUser.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"
                ));

        gameUserAuthorizationService.assertActiveGameUser(user);

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

        // 2. Xác thực JWT, tìm user local và kiểm tra quyền.
        User user = gameUserAuthorizationService
                .requireActiveGameUser(jwt);

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

    private static final List<String> GAME_HISTORY_STATUSES =
            List.of("COMPLETED", "FAILED", "ABANDONED");

    @Override
    @Transactional(readOnly = true)
    public GameHistoryPageResponse getMyGameHistory(
            Jwt jwt,
            int page,
            int size
    ) {
        // 1. Kiểm tra JWT
        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authentication token"
            );
        }

        // 2. Tìm user local theo Cognito sub
        User user = userRepository
                .findByCognitoSub(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User has not been synchronized"
                ));

        // 3. Chỉ tài khoản ACTIVE được xem lịch sử
        if (!"ACTIVE".equalsIgnoreCase(
                String.valueOf(user.getStatus()))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "User is not active"
            );
        }

        // Cho phép learner và admin xem lịch sử của chính mình
        String role = String.valueOf(user.getRole());

        if (!"learner".equalsIgnoreCase(role)
                && !"admin".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only learners and admins can access game history"
            );
        }

        // 4. Kiểm tra phân trang
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page must be greater than or equal to 0"
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "size must be between 1 and 100"
            );
        }

        // Tránh offset vượt giới hạn int của truy vấn JPA/Hibernate
        long offset = (long) page * size;

        if (offset > Integer.MAX_VALUE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Requested page offset is too large"
            );
        }

        // 5. Truy vấn lịch sử bằng ID từ user local
        Page<com.aws.alphabetuniverse.game.repository.projection.GameHistoryRow> historyPage =
                gameSessionRepository.findGameHistory(
                        user.getId(),
                        GAME_HISTORY_STATUSES,
                        PageRequest.of(page, size)
                );

        // 6. Map projection thành DTO
        List<GameHistoryItemResponse> content =
                historyPage.getContent()
                        .stream()
                        .map(this::toGameHistoryItem)
                        .toList();

        // 7. Trả metadata phân trang từ Page
        return new GameHistoryPageResponse(
                content,
                historyPage.getNumber(),
                historyPage.getSize(),
                historyPage.getTotalElements(),
                historyPage.getTotalPages()
        );
    }


    @Override
    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public Optional<GameSessionResumeResponse> getActiveSession(
            Long topicId,
            Jwt jwt
    ) {
        // 1. Validate topic ID.
        if (topicId == null || topicId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid topic ID"
            );
        }

        // 2. Xác thực JWT và kiểm tra quyền user local.
        User user = gameUserAuthorizationService
                .requireActiveGameUser(jwt);

        // 3. Tìm topic kể cả topic đã xóa mềm,
        // miễn bản ghi vẫn tồn tại và repository cho phép đọc.
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Topic not found"
                ));

        if (topic.getName() == null || topic.getName().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Topic name is missing"
            );
        }

        // 4. Tìm phiên active của đúng user + topic.
        // Không lọc theo mode và không khóa ghi.
        Optional<GameSession> sessionOptional =
                gameSessionRepository
                        .findByUser_IdAndTopic_IdAndStatus(
                                user.getId(),
                                topicId,
                                IN_PROGRESS
                        );

        if (sessionOptional.isEmpty()) {
            return Optional.empty();
        }

        GameSession session = sessionOptional.get();

        // 5. Kiểm tra quan hệ sở hữu và topic.
        if (session.getUser() == null
                || !user.getId().equals(session.getUser().getId())
                || session.getTopic() == null
                || !topicId.equals(session.getTopic().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Session ownership or topic data is inconsistent"
            );
        }

        // 6. Lấy attempts của đúng session theo ID tăng dần.
        List<SessionAttempt> attempts =
                sessionAttemptRepository
                        .findBySession_IdOrderByIdAsc(session.getId());

        // 7. Xác thực snapshot và tính tiến độ.
        GameSessionProgressResolverService.Progress progress =
                progressResolver.validateAndResolve(
                        session,
                        attempts
                );

        Long currentVocabularyId = progress.findNextUnresolved();

        if (currentVocabularyId == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No resumable question remains"
            );
        }

        int currentAttemptsUsed =
                progress.attemptsUsed(currentVocabularyId);

        int remainingAttempts = Math.max(
                0,
                progress.maxAttemptsPerWord() - currentAttemptsUsed
        );

        // 8. Chỉ trả các trường an toàn, không trả snapshot nguyên bản.
        List<GameResumeQuestionResponse> questions =
                progress.questionIds().stream()
                        .map(vocabularyId -> {
                            GameQuestionSnapshot snapshot =
                                    progress.snapshotById().get(vocabularyId);

                            return new GameResumeQuestionResponse(
                                    snapshot.vocabularyId(),
                                    snapshot.meaningVi(),
                                    snapshot.scrambledLetters(),
                                    snapshot.topicPosition(),
                                    progress.questionStatus(
                                            vocabularyId,
                                            currentVocabularyId
                                    ),
                                    progress.attemptsUsed(vocabularyId),
                                    progress.hasCorrectAttempt(vocabularyId)
                            );
                        })
                        .toList();

        GameSessionResumeResponse response =
                new GameSessionResumeResponse(
                        session.getId(),
                        topic.getId(),
                        topic.getName(),
                        progress.mode(),
                        session.getStatus(),
                        session.getBatchStartPosition(),
                        session.getBatchEndPosition(),
                        progress.questionIds().size(),
                        session.getScore(),
                        progress.resolvedQuestions(),
                        currentVocabularyId,
                        progress.maxAttemptsPerWord(),
                        currentAttemptsUsed,
                        remainingAttempts,
                        session.getStartedAt(),
                        questions
                );

        return Optional.of(response);
    }

    @Override
    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public GameProgressResponse getMyProgress(Jwt jwt) {

        // 1. Xác thực tài khoản hiện tại.
        User user = gameUserAuthorizationService
                .requireActiveGameUser(jwt);

        // 2. Lấy topic theo đúng thứ tự hiển thị.
        List<Topic> topics =
                topicRepository.findByActiveTrueOrderByOrderNoAscIdAsc();

        List<TopicProgressItemResponse> topicResponses =
                new ArrayList<>(topics.size());

        // 3. Tổng hợp tiến độ từng topic.
        for (Topic topic : topics) {

            Long topicId = topic.getId();

            // Lấy phiên đang hoạt động, không lọc theo mode.
            Optional<GameSession> activeSession =
                    gameSessionRepository
                            .findByUser_IdAndTopic_IdAndStatus(
                                    user.getId(),
                                    topicId,
                                    IN_PROGRESS
                            );

            Long activeSessionId = activeSession
                    .map(GameSession::getId)
                    .orElse(null);

            String activeSessionMode = activeSession
                    .map(GameSession::getMode)
                    .orElse(null);

            // Lấy từ vựng đang hoạt động theo thứ tự hiện tại.
            List<Vocabulary> activeVocabularies =
                    vocabularyRepository
                            .findByTopic_IdAndActiveTrueOrderByOrderNoAscIdAsc(
                                    topicId
                            );

            // Topic không có từ vựng vẫn trả thông tin phiên active.
            if (activeVocabularies.isEmpty()) {

                topicResponses.add(
                        new TopicProgressItemResponse(
                                topicId,
                                topic.getName(),
                                activeSessionId,
                                activeSessionMode,
                                0,
                                0,
                                null,
                                false,
                                List.of()
                        )
                );

                continue;
            }

            // 4. Đọc tiến độ học, tuyệt đối không ghi DB.
            int furthestPosition =
                    progressRepository
                            .findByUser_IdAndTopic_Id(
                                    user.getId(),
                                    topicId
                            )
                            .map(progress ->
                                    getFurthestPositionForRead(
                                            progress,
                                            topicId,
                                            activeVocabularies
                                    )
                            )
                            .orElse(0);

            Long furthestVocabularyId =
                    furthestPosition > 0
                            ? activeVocabularies
                              .get(furthestPosition - 1)
                              .getId()
                            : null;

            // 5. Lấy toàn bộ checkpoint đã thắng một lần.
            Set<Set<Long>> wonCheckpointIds =
                    checkpointService.getWonCheckpointVocabularyIdSets(
                            user.getId(),
                            topicId
                    );

            List<CheckpointProgressResponse> checkpoints =
                    new ArrayList<>();

            // 6. Tính trạng thái từng checkpoint trong bộ nhớ.
            for (int startIndex = 0;
                 startIndex < activeVocabularies.size();
                 startIndex += WORDS_PER_GAME) {

                int endIndexExclusive = Math.min(
                        startIndex + WORDS_PER_GAME,
                        activeVocabularies.size()
                );

                int checkpointNumber =
                        startIndex / WORDS_PER_GAME + 1;

                int startPosition = startIndex + 1;
                int endPosition = endIndexExclusive;

                Set<Long> currentVocabularyIds =
                        activeVocabularies
                                .subList(startIndex, endIndexExclusive)
                                .stream()
                                .map(Vocabulary::getId)
                                .collect(Collectors.toSet());

                // Đã thắng khi tập ID trùng chính xác.
                boolean passed =
                        wonCheckpointIds.contains(currentVocabularyIds);

                // Chỉ nhận IN_PROGRESS nếu đúng cả vị trí
                // lẫn bộ ID từ vựng của checkpoint hiện tại.
                boolean inProgress = activeSession
                        .map(session ->
                                isMatchingActiveCheckpoint(
                                        session,
                                        startPosition,
                                        endPosition,
                                        currentVocabularyIds
                                )
                        )
                        .orElse(false);

                String status;

                if (passed) {
                    status = "PASSED";
                } else if (inProgress) {
                    status = "IN_PROGRESS";
                } else if (endPosition <= furthestPosition) {
                    status = "AVAILABLE";
                } else {
                    status = "LOCKED";
                }

                checkpoints.add(
                        new CheckpointProgressResponse(
                                checkpointNumber,
                                startPosition,
                                endPosition,
                                status
                        )
                );
            }

            // 7. REVIEW chỉ mở khi đã học hết và thắng mọi checkpoint.
            boolean allCheckpointsPassed =
                    !checkpoints.isEmpty()
                            && checkpoints.stream()
                            .allMatch(checkpoint ->
                                    "PASSED".equals(checkpoint.status())
                            );

            boolean reviewUnlocked =
                    furthestPosition >= activeVocabularies.size()
                            && allCheckpointsPassed;

            // 8. Trả tiến độ của topic.
            topicResponses.add(
                    new TopicProgressItemResponse(
                            topicId,
                            topic.getName(),
                            activeSessionId,
                            activeSessionMode,
                            activeVocabularies.size(),
                            furthestPosition,
                            furthestVocabularyId,
                            reviewUnlocked,
                            List.copyOf(checkpoints)
                    )
            );
        }

        return new GameProgressResponse(List.copyOf(topicResponses));
    }


    private GameHistoryItemResponse toGameHistoryItem(
            com.aws.alphabetuniverse.game.repository.projection.GameHistoryRow row
    ) {
        String topicName = row.getTopicName();

        if (topicName == null || topicName.isBlank()) {
            topicName = "Chủ đề không còn tồn tại";
        }

        Integer score = row.getScore() == null
                ? 0
                : row.getScore();

        boolean won = Boolean.TRUE.equals(row.getWon());

        return new GameHistoryItemResponse(
                row.getSessionId(),
                row.getTopicId(),
                topicName,
                row.getMode(),
                row.getStatus(),
                score,
                won,
                row.getStartedAt(),
                row.getEndedAt()
        );
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

    private boolean isMatchingActiveCheckpoint(
            GameSession session,
            int startPosition,
            int endPosition,
            Set<Long> expectedVocabularyIds
    ) {
        return IN_PROGRESS.equals(session.getStatus())
                && CHECKPOINT.equals(session.getMode())
                && Objects.equals(
                session.getBatchStartPosition(),
                startPosition
        )
                && Objects.equals(
                session.getBatchEndPosition(),
                endPosition
        )
                && sameVocabularyIds(
                session.getQuestionVocabIds(),
                expectedVocabularyIds
        );
    }

    private boolean sameVocabularyIds(
            List<Long> storedIds,
            Set<Long> expectedIds
    ) {
        if (expectedIds == null
                || expectedIds.isEmpty()
                || !hasUniqueNonNullIds(storedIds)) {
            return false;
        }

        return storedIds.size() == expectedIds.size()
                && new HashSet<>(storedIds).equals(expectedIds);
    }

    private boolean hasUniqueNonNullIds(List<Long> ids) {
        return ids != null
                && !ids.isEmpty()
                && ids.stream().noneMatch(Objects::isNull)
                && new HashSet<>(ids).size() == ids.size();
    }

    private int getFurthestPositionForRead(
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

        // Trường hợp từ vựng vẫn còn hoạt động.
        for (int i = 0; i < activeVocabularies.size(); i++) {
            if (activeVocabularies.get(i).getId()
                    .equals(furthestVocabulary.getId())) {
                return i + 1;
            }
        }

        // Từ đã bị xóa mềm: tìm từ hoạt động gần nhất
        // theo orderNo mà không lưu thay đổi.
        Vocabulary replacement =
                findNearestActiveVocabularyAtOrBeforeOrderNo(
                        activeVocabularies,
                        furthestVocabulary.getOrderNo()
                );

        if (replacement == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Unable to resolve learning progress"
            );
        }

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
}
