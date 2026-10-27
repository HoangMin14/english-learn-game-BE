package com.aws.alphabetuniverse.leaderboard.dto;

import java.time.LocalDate;
import java.util.List;

public record WeeklyLeaderboardResponse(
        LocalDate weekStart,
        LocalDate weekEndExclusive,
        String timeZone,
        List<LeaderboardEntryResponse> entries,
        LeaderboardEntryResponse me
) {
}