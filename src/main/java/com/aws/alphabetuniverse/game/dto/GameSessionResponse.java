
package com.aws.alphabetuniverse.game.dto;

import com.aws.alphabetuniverse.game.enums.GameMode;

import java.time.Instant;
import java.util.List;

public record GameSessionResponse(
        Long sessionId,
        Long topicId,
        String topicName,
        GameMode mode,
        String status,
        int batchStartPosition,
        int batchEndPosition,
        int totalQuestions,
        Instant startedAt,
        List<GameQuestionResponse> questions
) {
}
