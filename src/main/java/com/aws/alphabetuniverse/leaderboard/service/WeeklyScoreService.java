
package com.aws.alphabetuniverse.leaderboard.service;

import java.time.Instant;

public interface WeeklyScoreService {

    public void synchronizeWeek(Long userId, Instant endedAt);
}
