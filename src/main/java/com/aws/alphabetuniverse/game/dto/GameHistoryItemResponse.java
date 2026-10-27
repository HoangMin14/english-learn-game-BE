package com.aws.alphabetuniverse.game.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GameHistoryItemResponse(
        Long sessionId,
        Long topicId,
        String topicName,
        String mode,
        String status,
        Integer score,
        @JsonProperty("isWon") Boolean won,
        Instant startedAt,
        Instant endedAt
) {
}