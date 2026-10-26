package com.aws.alphabetuniverse.game.service.impl;

import com.aws.alphabetuniverse.game.dto.SubmitAttemptRequest;
import com.aws.alphabetuniverse.game.dto.SubmitAttemptResponse;
import com.aws.alphabetuniverse.game.entity.GameSession;
import com.aws.alphabetuniverse.game.entity.GameQuestionSnapshot;
import com.aws.alphabetuniverse.game.entity.SessionAttempt;
import com.aws.alphabetuniverse.game.repository.GameConfigRepository;
import com.aws.alphabetuniverse.game.repository.GameSessionRepository;
import com.aws.alphabetuniverse.game.repository.SessionAttemptRepository;
import com.aws.alphabetuniverse.game.service.GameAttemptService;
import com.aws.alphabetuniverse.streak.service.StreakService;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.vocabulary.entity.Vocabulary;
import com.aws.alphabetuniverse.vocabulary.repository.VocabularyRepository;

import lombok.RequiredArgsConstructor;

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
public class GameAttemptServiceImpl implements GameAttemptService {

    private static final String CHECKPOINT = "CHECKPOINT";
    private static final String FREE_PLAY = "FREE_PLAY";
    private static final String REVIEW = "REVIEW";
    private static final int WORDS_PER_CHECKPOINT = 5;

    private final GameSessionRepository gameSessionRepository;
    private final SessionAttemptRepository sessionAttemptRepository;
    private final UserRepository userRepository;
    private final VocabularyRepository vocabularyRepository;
    private final GameConfigRepository gameConfigRepository;
    private final StreakService streakService;

    @Override
    @Transactional
    public SubmitAttemptResponse submitAttempt(
            Long sessionId,
            SubmitAttemptRequest request,
            Jwt jwt
    ) {
        // 0. Kiểm tra dữ liệu đầu vào để tránh NullPointerException.
        if (request == null
                || request.vocabularyId() == null
                || request.submittedLetters() == null
                || request.submittedLetters().isEmpty()
                || request.submittedLetters().size() > 100
                || request.submittedLetters().stream()
                .anyMatch(letter ->
                        letter == null || letter.isBlank())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid submitted attempt payload"
            );
        }

        // 1. Xác thực tài khoản từ JWT Cognito.
        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required"
            );
        }

        User user = userRepository.findByCognitoSub(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));

        // 2. Khóa phiên để tránh xử lý đồng thời.
        GameSession session = gameSessionRepository
                .findByIdForUpdate(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Game session not found"
                ));

        // Không tiết lộ phiên của tài khoản khác.
        if (session.getUser() == null
                || !user.getId().equals(session.getUser().getId())) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Game session not found"
            );
        }

        if (!"IN_PROGRESS".equals(session.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Game session is not in progress"
            );
        }

        String mode = session.getMode() == null
                ? ""
                : session.getMode().toUpperCase(Locale.ROOT);

        if (!Set.of(CHECKPOINT, FREE_PLAY, REVIEW).contains(mode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Game session has an invalid mode"
            );
        }


        // 3. Đọc danh sách ID theo đúng thứ tự đã lưu khi tạo phiên.
        List<Long> savedQuestionIds = session.getQuestionVocabIds();

        if (savedQuestionIds == null
                || savedQuestionIds.isEmpty()
                || savedQuestionIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(savedQuestionIds).size()
                != savedQuestionIds.size()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid saved question order. Create a new session."
            );
        }

        List<Long> questionIds = new ArrayList<>(savedQuestionIds);


        if (questionIds.isEmpty()
                || new HashSet<>(questionIds).size() != questionIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid saved question order. Create a new session."
            );
        }

        if (CHECKPOINT.equals(mode)
                && questionIds.size() > WORDS_PER_CHECKPOINT) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A checkpoint cannot contain more than 5 questions"
            );
        }

        // Lấy snapshot được lưu tại thời điểm tạo phiên.
        List<GameQuestionSnapshot> questionSnapshots =
                session.getQuestionSnapshot();

        if (questionSnapshots == null || questionSnapshots.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Game session has no question snapshot. "
                            + "Please create a new session."
            );
        }

        Map<Long, GameQuestionSnapshot> snapshotById = new HashMap<>();
        List<Long> snapshotIds = new ArrayList<>();

        for (GameQuestionSnapshot snapshot : questionSnapshots) {
            if (snapshot == null
                    || snapshot.vocabularyId() == null
                    || snapshot.word() == null
                    || snapshot.word().isBlank()
                    || snapshot.scrambledLetters() == null
                    || snapshot.scrambledLetters().isEmpty()
                    || snapshot.topicPosition() == null) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Game session contains an invalid question snapshot"
                );
            }

            if (snapshotById.putIfAbsent(
                    snapshot.vocabularyId(), snapshot) != null) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Duplicate vocabulary in question snapshot"
                );
            }

            snapshotIds.add(snapshot.vocabularyId());
        }

        // ID và thứ tự snapshot phải khớp danh sách câu hỏi của phiên.
        if (!snapshotIds.equals(questionIds)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Question snapshot does not match saved question order"
            );
        }

        Integer savedMaxAttempts = session.getMaxAttemptsPerWord();

        if (savedMaxAttempts == null || savedMaxAttempts < 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Game session has no valid attempt configuration. "
                            + "Please create a new session."
            );
        }

        int maxAttempts = savedMaxAttempts;

        List<SessionAttempt> attempts =
                sessionAttemptRepository
                        .findBySession_IdOrderByIdAsc(sessionId);

        // 4. Chỉ nhận đáp án của câu hiện tại.
        Long currentVocabularyId = findNextUnresolved(
                questionIds, attempts, maxAttempts, mode
        );

        if (currentVocabularyId == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "There are no unanswered questions"
            );
        }

        if (!currentVocabularyId.equals(request.vocabularyId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Answer the current question first"
            );
        }

        Vocabulary vocabulary = vocabularyRepository
                .findById(currentVocabularyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Vocabulary not found"
                ));

        GameQuestionSnapshot currentSnapshot =
                snapshotById.get(currentVocabularyId);

        if (currentSnapshot == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Current question snapshot not found"
            );
        }

        List<SessionAttempt> previousAttempts =
                attemptsFor(attempts, currentVocabularyId);

        int attemptNumber = previousAttempts.size() + 1;

        if (attemptNumber > maxAttempts) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Maximum attempts exceeded"
            );
        }

        // 5. Backend tự chấm đáp án.
        String submittedWord = request.submittedLetters()
                .stream()
                .map(letter -> letter.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.joining());

        String expectedWord = currentSnapshot.word()
                .trim()
                .toUpperCase(Locale.ROOT);

        boolean correct = expectedWord.equals(submittedWord);

        // 6. Lưu lần thử. Điểm mỗi câu luôn bằng 0.
        SessionAttempt attempt = new SessionAttempt();
        attempt.setSession(session);
        attempt.setVocabulary(vocabulary);
        attempt.setSubmittedLetters(submittedWord);
        attempt.setIsCorrect(correct);
        attempt.setPoints(0);
        attempt.setTimeMs(request.timeMs());

        SessionAttempt savedAttempt =
                sessionAttemptRepository.save(attempt);

        attempts.add(savedAttempt);

        int remainingAttempts =
                Math.max(0, maxAttempts - attemptNumber);

        boolean wordCompleted = correct;
        boolean sessionCompleted = false;
        Boolean sessionWon = null;
        int pointsEarned = 0;
        Integer currentStreak = null;
        String correctWord = null;

        Instant now = Instant.now();

        // 7. CHECKPOINT: sai hết số lượt thử được cấu hình thì thất bại ngay.
        if (CHECKPOINT.equals(mode)
                && !correct
                && attemptNumber >= maxAttempts) {

            wordCompleted = true;
            sessionCompleted = true;
            sessionWon = false;

            session.setStatus("FAILED");
            session.setWon(false);
            session.setScore(0);
            session.setLastEventAt(now);
            session.setEndedAt(now);

            gameSessionRepository.save(session);

            // Chỉ tiết lộ đáp án khi hết lượt.
            correctWord = currentSnapshot.word();

        } else {
            // FREE_PLAY/REVIEW: hết lượt thì bỏ qua từ.
            if (!correct
                    && attemptNumber >= maxAttempts
                    && !CHECKPOINT.equals(mode)) {
                wordCompleted = true;
                correctWord = currentSnapshot.word();
            }

            session.setLastEventAt(now);

            if (wordCompleted) {
                boolean allResolved = questionIds.stream()
                        .allMatch(id -> isResolved(
                                id, attempts, maxAttempts, mode
                        ));

                if (allResolved) {
                    sessionCompleted = true;

                    boolean allCorrect = questionIds.stream()
                            .allMatch(id -> hasCorrectAttempt(id, attempts));


                    if (CHECKPOINT.equals(mode)) {

                        if (allCorrect) {
                            int reward = 0;

                            // Chỉ kiểm tra cấu hình điểm, không phụ thuộc số câu.
                            // Nhóm cuối có ít hơn 5 từ vẫn được thưởng nếu thắng.
                            if (getRequiredBooleanConfig("score_enabled")) {

                                reward = getRequiredIntConfig(
                                        "checkpoint_pass_points"
                                );

                                if (reward < 0) {
                                    throw new ResponseStatusException(
                                            HttpStatus.INTERNAL_SERVER_ERROR,
                                            "Invalid checkpoint_pass_points configuration"
                                    );
                                }
                            }

                            sessionWon = true;
                            pointsEarned = reward;

                            session.setScore(reward);
                            session.setWon(true);
                            session.setStatus("COMPLETED");

                        } else {
                            sessionWon = false;

                            session.setScore(0);
                            session.setWon(false);
                            session.setStatus("FAILED");
                        }
                    } else {
                        // FREE_PLAY và REVIEW không có điểm thưởng.
                        sessionWon = allCorrect;
                        session.setScore(0);
                        session.setWon(allCorrect);
                        session.setStatus("COMPLETED");
                    }

                    session.setEndedAt(now);
                    session.setLastEventAt(now);

                    gameSessionRepository.save(session);


                } else {
                    gameSessionRepository.save(session);
                }

            } else {
                // Sai nhưng vẫn còn lượt thử.
                gameSessionRepository.save(session);
            }
        }

        // Chỉ tính streak khi thắng CHECKPOINT đủ 5/5 từ.
        if (sessionCompleted
                && Boolean.TRUE.equals(sessionWon)
                && "CHECKPOINT".equals(mode)
                && questionIds.size() == 5
                && getRequiredBooleanConfig("streak_enabled")) {

            currentStreak =
                    streakService.recordActivity(user.getId());
        }

        // 8. Trả ID câu tiếp theo, nếu câu hiện tại đã được xử lý xong.
        Long nextVocabularyId = null;

        if (wordCompleted && !sessionCompleted) {
            nextVocabularyId = findNextUnresolved(
                    questionIds, attempts, maxAttempts, mode
            );
        }

        return new SubmitAttemptResponse(
                session.getId(),
                currentVocabularyId,
                correct,
                attemptNumber,
                remainingAttempts,
                wordCompleted,
                sessionCompleted,
                sessionWon,
                pointsEarned,
                session.getScore() == null ? 0 : session.getScore(),
                nextVocabularyId,
                correctWord,
                currentStreak
        );
    }


    private List<SessionAttempt> attemptsFor(
            List<SessionAttempt> attempts,
            Long vocabularyId
    ) {
        return attempts.stream()
                .filter(attempt ->
                        attempt.getVocabulary().getId().equals(vocabularyId))
                .toList();
    }

    private boolean hasCorrectAttempt(
            Long vocabularyId,
            List<SessionAttempt> attempts
    ) {
        return attemptsFor(attempts, vocabularyId)
                .stream()
                .anyMatch(attempt ->
                        Boolean.TRUE.equals(attempt.getIsCorrect()));
    }

    private boolean isResolved(
            Long vocabularyId,
            List<SessionAttempt> attempts,
            int maxAttempts,
            String mode
    ) {
        if (hasCorrectAttempt(vocabularyId, attempts)) {
            return true;
        }

        if (!CHECKPOINT.equals(mode)) {
            return attemptsFor(attempts, vocabularyId).size() >= maxAttempts;
        }

        return false;
    }

    private Long findNextUnresolved(
            List<Long> questionIds,
            List<SessionAttempt> attempts,
            int maxAttempts,
            String mode
    ) {
        return questionIds.stream()
                .filter(id -> !isResolved(
                        id, attempts, maxAttempts, mode
                ))
                .findFirst()
                .orElse(null);
    }


    private int getRequiredIntConfig(String key) {
        String value = gameConfigRepository
                .findByConfigKeyAndIsActiveTrue(key)
                .map(config -> config.getConfigValue())
                .filter(configValue -> !configValue.isBlank())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Missing or empty active game configuration: " + key
                ));

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Invalid integer configuration: " + key
            );
        }
    }

    private boolean getRequiredBooleanConfig(String key) {
        String value = gameConfigRepository
                .findByConfigKeyAndIsActiveTrue(key)
                .map(config -> config.getConfigValue())
                .filter(configValue -> !configValue.isBlank())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Missing or empty active game configuration: " + key
                ));

        value = value.trim();

        if (!"true".equalsIgnoreCase(value)
                && !"false".equalsIgnoreCase(value)) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Invalid boolean configuration: " + key
            );
        }

        return Boolean.parseBoolean(value);
    }


}