
package com.aws.alphabetuniverse.game.controller;

import com.aws.alphabetuniverse.admin.service.AdminAccessService;
import com.aws.alphabetuniverse.game.dto.admin.GameConfigAdminResponse;
import com.aws.alphabetuniverse.game.dto.admin.GameConfigUpdateRequest;
import com.aws.alphabetuniverse.game.service.GameConfigService;

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

@RestController
@RequestMapping("/api/admin/game-configs")
@RequiredArgsConstructor
@Tag(name = "Admin-config-game")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminGameConfigController {

    private final GameConfigService gameConfigService;
    private final AdminAccessService adminAccessService;

    // GET global config.
    @GetMapping("/global")
    public List<GameConfigAdminResponse> getGlobalConfigs(
            @AuthenticationPrincipal Jwt jwt
    ) {
        adminAccessService.requireActiveAdmin(jwt);

        return gameConfigService.getGlobalConfigs();
    }

    // PUT global config.
    @PutMapping("/global/{configKey}")
    public GameConfigAdminResponse updateGlobalConfig(
            @PathVariable String configKey,
            @RequestBody GameConfigUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        adminAccessService.requireActiveAdmin(jwt);

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request body is required"
            );
        }

        if (Boolean.FALSE.equals(request.active())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Global config cannot be deactivated. " +
                            "Set score_enabled or streak_enabled to false instead."
            );
        }

        return gameConfigService.updateGlobalConfig(
                configKey,
                request.configValue()
        );
    }

    // GET config theo topic, bao gồm global và giá trị hiệu lực.
    @GetMapping("/topics/{topicId}")
    public List<GameConfigAdminResponse> getTopicConfigs(
            @PathVariable Long topicId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        adminAccessService.requireActiveAdmin(jwt);

        return gameConfigService.getTopicConfigs(topicId);
    }

    // PUT override theo topic.
    @PutMapping("/topics/{topicId}/{configKey}")
    public GameConfigAdminResponse updateTopicConfig(
            @PathVariable Long topicId,
            @PathVariable String configKey,
            @RequestBody GameConfigUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        adminAccessService.requireActiveAdmin(jwt);

        if (request == null || request.active() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Request body must contain configValue and active"
            );
        }

        return gameConfigService.updateTopicConfig(
                topicId,
                configKey,
                request.configValue(),
                request.active()
        );
    }

    // Xóa override của bốn key được hỗ trợ.
    // Sau đó topic quay về dùng global config.
    @DeleteMapping("/topics/{topicId}")
    public ResponseEntity<Void> resetTopicConfigs(
            @PathVariable Long topicId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        adminAccessService.requireActiveAdmin(jwt);

        gameConfigService.resetTopicConfig(topicId);

        return ResponseEntity.noContent().build();
    }


    @DeleteMapping("/topics/{topicId}/{configKey}")
    public ResponseEntity<Void> resetTopicConfig(
            @PathVariable Long topicId,
            @PathVariable String configKey,
            @AuthenticationPrincipal Jwt jwt
    ) {
        adminAccessService.requireActiveAdmin(jwt);

        gameConfigService.resetTopicConfig(
                topicId,
                configKey
        );

        return ResponseEntity.noContent().build();
    }

}
