
package com.aws.alphabetuniverse.game.service.impl;

import com.aws.alphabetuniverse.game.entity.GameQuestionSnapshot;
import com.aws.alphabetuniverse.game.entity.GameSession;
import com.aws.alphabetuniverse.game.entity.SessionAttempt;
import com.aws.alphabetuniverse.game.service.GameSessionProgressResolverService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class GameSessionProgressResolverServiceImpl
        implements GameSessionProgressResolverService {

    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String CHECKPOINT = "CHECKPOINT";
    private static final String FREE_PLAY = "FREE_PLAY";
    private static final String REVIEW = "REVIEW";

    private static final int WORDS_PER_CHECKPOINT = 5;

    @Override
    public Progress validateAndResolve(
            GameSession session,
            List<SessionAttempt> loadedAttempts
    ) {
        if (session == null
                || !IN_PROGRESS.equals(session.getStatus())) {
            throw conflict("Game session is not in progress");
        }

        String mode = session.getMode() == null
                ? ""
                : session.getMode().trim()
                  .toUpperCase(Locale.ROOT);

        if (!Set.of(CHECKPOINT, FREE_PLAY, REVIEW).contains(mode)) {
            throw conflict("Game session has an invalid mode");
        }

        // 1. Kiểm tra danh sách ID câu hỏi đã lưu.
        List<Long> savedIds = session.getQuestionVocabIds();

        if (savedIds == null
                || savedIds.isEmpty()
                || savedIds.stream().anyMatch(
                id -> id == null || id <= 0)
                || new HashSet<>(savedIds).size() != savedIds.size()) {
            throw conflict("Saved question IDs are invalid");
        }

        List<Long> questionIds = new ArrayList<>(savedIds);

        if (CHECKPOINT.equals(mode)
                && questionIds.size() > WORDS_PER_CHECKPOINT) {
            throw conflict("Checkpoint contains more than 5 questions");
        }

        // 2. Kiểm tra metadata.
        Integer maxAttempts = session.getMaxAttemptsPerWord();

        if (maxAttempts == null || maxAttempts < 1) {
            throw conflict("Maximum attempts configuration is invalid");
        }

        if (session.getScore() == null
                || session.getScore() < 0
                || session.getStartedAt() == null
                || session.getEndedAt() != null) {
            throw conflict("Session metadata is inconsistent");
        }

        // Batch position chỉ bắt buộc đối với CHECKPOINT.
        if (CHECKPOINT.equals(mode)) {
            Integer batchStart = session.getBatchStartPosition();
            Integer batchEnd = session.getBatchEndPosition();

            if (batchStart == null
                    || batchEnd == null
                    || batchStart < 1
                    || batchEnd < batchStart) {
                throw conflict("Checkpoint positions are invalid");
            }
        }

        // 3. Kiểm tra snapshot đã lưu.
        List<GameQuestionSnapshot> snapshots =
                session.getQuestionSnapshot();

        if (snapshots == null
                || snapshots.size() != questionIds.size()) {
            throw conflict(
                    "Question snapshot is missing or incomplete");
        }

        Map<Long, GameQuestionSnapshot> snapshotById =
                new LinkedHashMap<>();

        List<Long> snapshotIds = new ArrayList<>();
        Set<Integer> topicPositions = new HashSet<>();

        for (GameQuestionSnapshot snapshot : snapshots) {
            if (snapshot == null
                    || snapshot.vocabularyId() == null
                    || snapshot.vocabularyId() <= 0
                    || snapshot.word() == null
                    || snapshot.word().isBlank()
                    || snapshot.meaningVi() == null
                    || snapshot.meaningVi().isBlank()
                    || snapshot.scrambledLetters() == null
                    || snapshot.scrambledLetters().isEmpty()
                    || snapshot.scrambledLetters().stream()
                    .anyMatch(letter ->
                            letter == null || letter.isBlank())
                    || snapshot.topicPosition() == null
                    || snapshot.topicPosition() < 1) {
                throw conflict("Question snapshot contains invalid data");
            }

            if (snapshotById.putIfAbsent(
                    snapshot.vocabularyId(), snapshot) != null) {
                throw conflict("Duplicate vocabulary ID in snapshot");
            }

            if (!topicPositions.add(snapshot.topicPosition())) {
                throw conflict("Duplicate topic position in snapshot");
            }

            snapshotIds.add(snapshot.vocabularyId());
        }

        // ID và thứ tự snapshot phải khớp dữ liệu đã lưu.
        if (!snapshotIds.equals(questionIds)) {
            throw conflict(
                    "Snapshot does not match saved question order");
        }

        // 4. Kiểm tra attempts theo thứ tự ID tăng dần.
        List<SessionAttempt> attempts = loadedAttempts == null
                ? new ArrayList<>()
                : new ArrayList<>(loadedAttempts);

        Map<Long, Integer> attemptsCount = new HashMap<>();
        Set<Long> correctlyAnswered = new HashSet<>();

        for (SessionAttempt attempt : attempts) {
            if (attempt == null
                    || attempt.getVocabulary() == null
                    || attempt.getVocabulary().getId() == null
                    || attempt.getIsCorrect() == null) {
                throw conflict("Attempt contains invalid data");
            }

            Long vocabularyId = attempt.getVocabulary().getId();

            if (!snapshotById.containsKey(vocabularyId)) {
                throw conflict(
                        "Attempt references a question outside the snapshot");
            }

            // Không chấp nhận attempt sau khi câu đã đúng.
            if (correctlyAnswered.contains(vocabularyId)) {
                throw conflict(
                        "Attempt exists after a correct answer");
            }

            int count = attemptsCount.merge(
                    vocabularyId, 1, Integer::sum);

            if (count > maxAttempts) {
                throw conflict(
                        "Question exceeded maximum attempts");
            }

            if (Boolean.TRUE.equals(attempt.getIsCorrect())) {
                correctlyAnswered.add(vocabularyId);
            }
        }

        ProgressImpl progress = new ProgressImpl(
                mode,
                questionIds,
                snapshotById,
                maxAttempts,
                attempts
        );

        progress.validateCurrentState();

        return progress;
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message + ". Abandon this session and create a new one."
        );
    }

    private static final class ProgressImpl implements Progress {

        private final String mode;
        private final List<Long> questionIds;
        private final Map<Long, GameQuestionSnapshot> snapshotById;
        private final int maxAttemptsPerWord;
        private final List<SessionAttempt> attempts;

        private ProgressImpl(
                String mode,
                List<Long> questionIds,
                Map<Long, GameQuestionSnapshot> snapshotById,
                int maxAttemptsPerWord,
                List<SessionAttempt> attempts
        ) {
            this.mode = mode;
            this.questionIds = List.copyOf(questionIds);
            this.snapshotById = Collections.unmodifiableMap(
                    new LinkedHashMap<>(snapshotById));
            this.maxAttemptsPerWord = maxAttemptsPerWord;
            this.attempts = new ArrayList<>(attempts);
        }

        @Override
        public String mode() {
            return mode;
        }

        @Override
        public List<Long> questionIds() {
            return questionIds;
        }

        @Override
        public Map<Long, GameQuestionSnapshot> snapshotById() {
            return snapshotById;
        }

        @Override
        public int maxAttemptsPerWord() {
            return maxAttemptsPerWord;
        }

        @Override
        public List<SessionAttempt> attempts() {
            // Mutable để POST submit thêm attempt vừa lưu.
            return attempts;
        }

        @Override
        public List<SessionAttempt> attemptsFor(Long vocabularyId) {
            return attempts.stream()
                    .filter(attempt ->
                            vocabularyId.equals(
                                    attempt.getVocabulary().getId()))
                    .toList();
        }

        @Override
        public int attemptsUsed(Long vocabularyId) {
            return attemptsFor(vocabularyId).size();
        }

        @Override
        public boolean hasCorrectAttempt(Long vocabularyId) {
            return attemptsFor(vocabularyId).stream()
                    .anyMatch(attempt ->
                            Boolean.TRUE.equals(
                                    attempt.getIsCorrect()));
        }

        @Override
        public boolean isResolved(Long vocabularyId) {
            if (hasCorrectAttempt(vocabularyId)) {
                return true;
            }

            // CHECKPOINT sai hết lượt phải kết thúc phiên,
            // không xem là câu đã giải quyết trong phiên active.
            if (CHECKPOINT.equals(mode)) {
                return false;
            }

            // FREE_PLAY/REVIEW sai hết lượt thì bỏ qua câu.
            return attemptsUsed(vocabularyId)
                    >= maxAttemptsPerWord;
        }

        @Override
        public Long findNextUnresolved() {
            return questionIds.stream()
                    .filter(id -> !isResolved(id))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public int resolvedQuestions() {
            return (int) questionIds.stream()
                    .filter(this::isResolved)
                    .count();
        }

        @Override
        public String questionStatus(
                Long vocabularyId,
                Long currentVocabularyId
        ) {
            if (hasCorrectAttempt(vocabularyId)) {
                return "COMPLETED";
            }

            if (!CHECKPOINT.equals(mode)
                    && attemptsUsed(vocabularyId)
                    >= maxAttemptsPerWord) {
                return "SKIPPED";
            }

            if (vocabularyId.equals(currentVocabularyId)) {
                return "CURRENT";
            }

            return "PENDING";
        }

        private void validateCurrentState() {
            Long currentId = findNextUnresolved();

            if (currentId == null) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "All questions are resolved but session is still IN_PROGRESS"
                );
            }

            if (CHECKPOINT.equals(mode)
                    && attemptsUsed(currentId)
                    >= maxAttemptsPerWord) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Checkpoint exhausted attempts but is still IN_PROGRESS"
                );
            }

            int currentIndex = questionIds.indexOf(currentId);

            for (int i = currentIndex + 1;
                 i < questionIds.size();
                 i++) {
                if (attemptsUsed(questionIds.get(i)) > 0) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Attempts exist after the current unresolved question"
                    );
                }
            }
        }
    }
}
