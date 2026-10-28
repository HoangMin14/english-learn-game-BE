package com.aws.alphabetuniverse.game.dto;

public record CheckpointProgressResponse(
        int checkpointNumber,
        int startPosition,
        int endPosition,
        String status
) {
}