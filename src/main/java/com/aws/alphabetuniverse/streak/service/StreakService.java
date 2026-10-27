package com.aws.alphabetuniverse.streak.service;

import com.aws.alphabetuniverse.streak.dto.UserStreakResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface StreakService {
    public int recordActivity(Long userId);
    public UserStreakResponse getMyStreak(Jwt jwt);
}
