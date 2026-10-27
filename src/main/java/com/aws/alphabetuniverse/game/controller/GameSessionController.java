
package com.aws.alphabetuniverse.game.controller;

import com.aws.alphabetuniverse.game.dto.CreateGameSessionRequest;
import com.aws.alphabetuniverse.game.dto.GameSessionResponse;
import com.aws.alphabetuniverse.game.dto.GameSessionResumeResponse;
import com.aws.alphabetuniverse.game.service.GameSessionService;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/topics")
@Tag(name = "Game")
@RequiredArgsConstructor
public class GameSessionController {

    private final GameSessionService gameSessionService;

    @PostMapping("/{topicId}/game-sessions")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<GameSessionResponse> createGameSession(
            @PathVariable Long topicId,
            @Valid @RequestBody CreateGameSessionRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        GameSessionResponse response =
                gameSessionService.createSession(
                        topicId,
                        request,
                        jwt
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @DeleteMapping("/{topicId}/game-sessions/active")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Void> abandonActiveSession(
            @PathVariable Long topicId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        gameSessionService.abandonActiveSession(topicId, jwt);

        return ResponseEntity.noContent().build();
    }


    @GetMapping("/{topicId}/game-sessions/active")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<GameSessionResumeResponse> getActiveSession(
            @PathVariable Long topicId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Optional<GameSessionResumeResponse> result =
                gameSessionService.getActiveSession(topicId, jwt);

        if (result.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(result.get());
    }

}
