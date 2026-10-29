
package com.aws.alphabetuniverse.game.dto;

import java.time.Instant;

public record GameHistoryAttemptDetailResponse(
        Long attemptId,
        int attemptNumber,
        String submittedLetters,
        boolean correct,
        Integer timeMs,
        Instant createdAt
) {
}
