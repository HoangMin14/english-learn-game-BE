package com.aws.alphabetuniverse.game.controller;

import com.aws.alphabetuniverse.game.dto.SubmitAttemptRequest;
import com.aws.alphabetuniverse.game.dto.SubmitAttemptResponse;
import com.aws.alphabetuniverse.game.service.GameAttemptService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game-sessions")
@RequiredArgsConstructor
@Tag(name = "Game-Attempt")
@SecurityRequirement(name = "Bearer Authentication")
public class GameAttemptController {

    private final GameAttemptService gameAttemptService;

    @PostMapping("/{sessionId}/attempts")
    public ResponseEntity<SubmitAttemptResponse> submitAttempt(
            @PathVariable Long sessionId,
            @Valid @RequestBody SubmitAttemptRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        SubmitAttemptResponse response =
                gameAttemptService.submitAttempt(
                        sessionId,
                        request,
                        jwt
                );

        return ResponseEntity.ok(response);
    }
}