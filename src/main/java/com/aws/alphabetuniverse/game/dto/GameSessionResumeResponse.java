
package com.aws.alphabetuniverse.game.dto;

import java.time.Instant;
import java.util.List;

public record GameSessionResumeResponse(
        Long sessionId,
        Long topicId,
        String topicName,
        String mode,
        String status,
        Integer batchStartPosition,
        Integer batchEndPosition,
        int questionCount,
        Integer score,
        int resolvedQuestions,
        Long currentVocabularyId,
        int maxAttemptsPerWord,
        int currentAttemptsUsed,
        int remainingAttempts,
        Instant startedAt,
        List<GameResumeQuestionResponse> questions
) {
}
