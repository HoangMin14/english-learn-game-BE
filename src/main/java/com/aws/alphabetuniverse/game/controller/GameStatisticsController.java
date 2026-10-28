package com.aws.alphabetuniverse.game.controller;

import com.aws.alphabetuniverse.game.dto.GameStatisticsResponse;
import com.aws.alphabetuniverse.game.service.GameStatisticsService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@Tag(name = "Game-Statistics")
@SecurityRequirement(name = "Bearer Authentication")
public class GameStatisticsController {

    private final GameStatisticsService gameStatisticsService;

    @GetMapping("/game-statistics")
    public GameStatisticsResponse getMyGameStatistics(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return gameStatisticsService.getMyGameStatistics(jwt);
    }
}