package com.aws.alphabetuniverse.streak.controller;

import com.aws.alphabetuniverse.streak.dto.UserStreakResponse;
import com.aws.alphabetuniverse.streak.service.StreakService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Streak")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class StreakController {

    private final StreakService streakService;

    @GetMapping("/streak")
    public UserStreakResponse getMyStreak(
            @AuthenticationPrincipal Jwt jwt) {

        return streakService.getMyStreak(jwt);
    }
}