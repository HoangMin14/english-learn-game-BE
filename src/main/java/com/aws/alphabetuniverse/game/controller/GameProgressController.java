package com.aws.alphabetuniverse.game.controller;

import com.aws.alphabetuniverse.game.dto.GameProgressResponse;
import com.aws.alphabetuniverse.game.service.GameSessionService;

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
@Tag(name = "Progress")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class GameProgressController {

    private final GameSessionService gameSessionService;

    @GetMapping("/progress")
    public GameProgressResponse getMyProgress(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return gameSessionService.getMyProgress(jwt);
    }
}