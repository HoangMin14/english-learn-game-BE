package com.aws.alphabetuniverse.game.dto;

public record EffectiveGameConfig(
        int maxAttemptsPerWord,
        int checkpointPassPoints,
        boolean scoreEnabled,
        boolean streakEnabled
) {
}