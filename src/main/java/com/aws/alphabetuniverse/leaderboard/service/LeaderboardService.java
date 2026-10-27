package com.aws.alphabetuniverse.leaderboard.service;

import com.aws.alphabetuniverse.leaderboard.dto.WeeklyLeaderboardResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface LeaderboardService {

    public WeeklyLeaderboardResponse getWeeklyLeaderboard(
            Jwt jwt,
            int limit
    );
}