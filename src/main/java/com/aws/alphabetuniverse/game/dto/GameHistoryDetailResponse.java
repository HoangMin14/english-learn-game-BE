
package com.aws.alphabetuniverse.game.dto;

import java.time.Instant;
import java.util.List;

public record GameHistoryDetailResponse(
        Long sessionId,
        Long topicId,
        String topicName,
        String mode,
        String status,
        Integer score,
        boolean won,
        Instant startedAt,
        Instant endedAt,
        Integer maxAttemptsPerWord,
        int questionCount,
        boolean historyComplete,
        List<GameHistoryQuestionDetailResponse> questions
) {
}
