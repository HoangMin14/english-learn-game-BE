package com.aws.alphabetuniverse.leaderboard.controller;

import com.aws.alphabetuniverse.leaderboard.dto.WeeklyLeaderboardResponse;
import com.aws.alphabetuniverse.leaderboard.service.LeaderboardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
@Tag(name = "Leaderboard")
@SecurityRequirement(name = "Bearer Authentication")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping("/weekly")
    public WeeklyLeaderboardResponse getWeeklyLeaderboard(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Số người hiển thị, từ 1 đến 100")
            @RequestParam(defaultValue = "10") int limit
    ) {
        return leaderboardService.getWeeklyLeaderboard(
                jwt,
                limit
        );
    }
}