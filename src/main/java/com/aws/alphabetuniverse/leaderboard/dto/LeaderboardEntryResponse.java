package com.aws.alphabetuniverse.leaderboard.dto;

public record LeaderboardEntryResponse(
        Long userId,
        String displayName,
        Long rank,
        Long totalScore
) {
}