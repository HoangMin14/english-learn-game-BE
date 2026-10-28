package com.aws.alphabetuniverse.game.dto;

import java.math.BigDecimal;

public record GameStatisticsResponse(
        long totalSessions,
        long wonSessions,
        long totalCheckpointScore,
        long totalAttempts,
        long correctAttempts,
        BigDecimal accuracyRate
) {
}