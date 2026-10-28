package com.aws.alphabetuniverse.game.service;

import com.aws.alphabetuniverse.game.dto.GameStatisticsResponse;

import org.springframework.security.oauth2.jwt.Jwt;

public interface GameStatisticsService {

    public GameStatisticsResponse getMyGameStatistics(Jwt jwt);
}