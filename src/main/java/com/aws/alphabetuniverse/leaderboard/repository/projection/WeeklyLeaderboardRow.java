package com.aws.alphabetuniverse.leaderboard.repository.projection;

public interface WeeklyLeaderboardRow {

    Long getUserId();

    String getDisplayName();

    Long getTotalScore();

    Long getRank();

    Long getPosition();
}