package com.aws.alphabetuniverse.game.controller;

import com.aws.alphabetuniverse.game.dto.GameHistoryDetailResponse;
import com.aws.alphabetuniverse.game.dto.GameHistoryPageResponse;
import com.aws.alphabetuniverse.game.service.GameSessionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
@Tag(name = "Game-History")
@SecurityRequirement(name = "Bearer Authentication")
public class GameHistoryController {

    private final GameSessionService gameSessionService;

    @GetMapping("/game-history")
    public GameHistoryPageResponse getMyGameHistory(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Số trang, bắt đầu từ 0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số bản ghi mỗi trang, từ 1 đến 100")
            @RequestParam(defaultValue = "10") int size
    ) {
        return gameSessionService.getMyGameHistory(
                jwt,
                page,
                size
        );
    }


    @GetMapping("/game-history/{sessionId}")
    public GameHistoryDetailResponse getMyGameHistoryDetail(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return gameSessionService.getMyGameHistoryDetail(
                sessionId,
                jwt
        );
    }

}