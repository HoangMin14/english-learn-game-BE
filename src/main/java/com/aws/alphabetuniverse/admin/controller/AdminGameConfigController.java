package com.aws.alphabetuniverse.admin.controller;

import com.aws.alphabetuniverse.admin.service.AdminAccessService;
import com.aws.alphabetuniverse.game.dto.admin.GameConfigAdminResponse;
import com.aws.alphabetuniverse.game.dto.admin.GameConfigAuditContext;
import com.aws.alphabetuniverse.game.dto.admin.GameConfigUpdateRequest;
import com.aws.alphabetuniverse.game.service.GameConfigService;
import com.aws.alphabetuniverse.user.entity.User;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/game-configs")
@RequiredArgsConstructor
@Tag(name = "Admin-config-game")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminGameConfigController {

    private final GameConfigService gameConfigService;
    private final AdminAccessService adminAccessService;


    // ==========================================
    // 1. GET GLOBAL CONFIG
    // ==========================================

    @GetMapping("/global")
    public List<GameConfigAdminResponse> getGlobalConfigs(
            @AuthenticationPrincipal Jwt jwt
    ) {

        adminAccessService.requireActiveAdmin(jwt);

        return gameConfigService.getGlobalConfigs();
    }


    // ==========================================
    // 2. PUT GLOBAL CONFIG
    // ==========================================

    @PutMapping("/global/{configKey}")
    public GameConfigAdminResponse updateGlobalConfig(
            @PathVariable String configKey,
            @RequestBody(required = false) GameConfigUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {

        // Kiểm tra Admin và tạo Audit Context.
        GameConfigAuditContext context = createAuditContext(jwt);

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request body is required"
            );
        }

        // Global config luôn phải ACTIVE.
        if (Boolean.FALSE.equals(request.active())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Global config cannot be deactivated. " +
                            "Set score_enabled or streak_enabled to false instead."
            );
        }

        return gameConfigService.updateGlobalConfig(
                configKey,
                request.configValue(),
                request.description(),
                context
        );
    }


    // ==========================================
    // 3. GET CONFIG THEO TOPIC
    // ==========================================

    @GetMapping("/topics/{topicId}")
    public List<GameConfigAdminResponse> getTopicConfigs(
            @PathVariable Long topicId,
            @AuthenticationPrincipal Jwt jwt
    ) {

        adminAccessService.requireActiveAdmin(jwt);

        return gameConfigService.getTopicConfigs(topicId);
    }


    // ==========================================
    // 4. PUT CONFIG OVERRIDE THEO TOPIC
    // ==========================================

    @PutMapping("/topics/{topicId}/{configKey}")
    public GameConfigAdminResponse updateTopicConfig(
            @PathVariable Long topicId,
            @PathVariable String configKey,
            @RequestBody(required = false) GameConfigUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {

        if (request == null || request.active() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request body must contain configValue and active"
            );
        }

        // Kiểm tra Admin và tạo Audit Context.
        GameConfigAuditContext context = createAuditContext(jwt);

        return gameConfigService.updateTopicConfig(
                topicId,
                configKey,
                request.configValue(),
                request.description(),
                request.active(),
                context
        );
    }


    // ==========================================
    // 5. DELETE TOÀN BỘ CONFIG OVERRIDE CỦA TOPIC
    // ==========================================

    @DeleteMapping("/topics/{topicId}")
    public ResponseEntity<Void> resetTopicConfigs(
            @PathVariable Long topicId,
            @AuthenticationPrincipal Jwt jwt
    ) {

        // Mỗi lần reset tạo một operationId riêng.
        GameConfigAuditContext context = createAuditContext(jwt);

        gameConfigService.resetTopicConfig(
                topicId,
                context
        );

        return ResponseEntity.noContent().build();
    }


    // ==========================================
    // 6. DELETE MỘT CONFIG OVERRIDE
    // ==========================================

    @DeleteMapping("/topics/{topicId}/{configKey}")
    public ResponseEntity<Void> resetTopicConfig(
            @PathVariable Long topicId,
            @PathVariable String configKey,
            @AuthenticationPrincipal Jwt jwt
    ) {

        GameConfigAuditContext context = createAuditContext(jwt);

        gameConfigService.resetTopicConfig(
                topicId,
                configKey,
                context
        );

        return ResponseEntity.noContent().build();
    }


    // ==========================================
    // 7. CREATE AUDIT CONTEXT
    // ==========================================

    private GameConfigAuditContext createAuditContext(Jwt jwt) {

        User admin = adminAccessService.requireActiveAdmin(jwt);

        return new GameConfigAuditContext(
                admin.getId(),
                jwt.getSubject(),
                UUID.randomUUID()
        );
    }
}