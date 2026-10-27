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
import com.aws.alphabetuniverse.game.service.GameSessionProgressResolverService;
import com.aws.alphabetuniverse.game.service.GameUserAuthorizationService;
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
    private final GameUserAuthorizationService gameUserAuthorizationService;
    private final GameSessionProgressResolverService progressResolver;

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

        User authenticatedUser =
                gameUserAuthorizationService.findAuthenticatedUser(jwt);

        User user = userRepository
                .findByIdForUpdate(authenticatedUser.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User not found"
                ));

        gameUserAuthorizationService.assertActiveGameUser(user);

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


        // 3. Đọc attempts của session theo ID tăng dần.
        List<SessionAttempt> loadedAttempts =
                sessionAttemptRepository
                        .findBySession_IdOrderByIdAsc(sessionId);

        // 4. Dùng helper chung để kiểm tra snapshot
        // và khôi phục tiến độ hiện tại.
        GameSessionProgressResolverService.Progress progress =
                progressResolver.validateAndResolve(
                        session,
                        loadedAttempts
                );

        String mode = progress.mode();

        // Giữ nguyên thứ tự câu hỏi đã lưu khi tạo session.
        List<Long> questionIds = progress.questionIds();

        Map<Long, GameQuestionSnapshot> snapshotById =
                progress.snapshotById();

        int maxAttempts = progress.maxAttemptsPerWord();

        // Danh sách mutable do Progress quản lý.
        // Phải thêm attempt mới vào danh sách này sau khi save.
        List<SessionAttempt> attempts = progress.attempts();

        // Xác định câu chưa xử lý xong đầu tiên.
        Long currentVocabularyId = progress.findNextUnresolved();

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

        int attemptNumber =
                progress.attemptsUsed(currentVocabularyId) + 1;

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
                        .allMatch(progress::isResolved);

                if (allResolved) {
                    sessionCompleted = true;

                    boolean allCorrect = questionIds.stream()
                            .allMatch(progress::hasCorrectAttempt);


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
            nextVocabularyId = progress.findNextUnresolved();
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