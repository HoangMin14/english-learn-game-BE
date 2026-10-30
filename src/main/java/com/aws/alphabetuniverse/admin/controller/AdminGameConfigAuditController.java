package com.aws.alphabetuniverse.admin.controller;

import com.aws.alphabetuniverse.admin.service.AdminAccessService;
import com.aws.alphabetuniverse.admin.service.GameConfigAuditService;

import com.aws.alphabetuniverse.game.dto.admin.GameConfigAuditDetailResponse;
import com.aws.alphabetuniverse.game.dto.admin.GameConfigAuditPageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/game-configs/audits")
@RequiredArgsConstructor
@Tag(name = "Admin-Game-Config-Audit")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminGameConfigAuditController {

    private final GameConfigAuditService auditService;

    private final AdminAccessService adminAccessService;


    // =====================================================
    // API 1: GET ALL AUDIT HISTORY
    // =====================================================

    @GetMapping
    public GameConfigAuditPageResponse getAudits(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            @RequestParam(required = false)
            String scope,

            @RequestParam(required = false)
            Long topicId,

            @RequestParam(required = false)
            String configKey,

            @RequestParam(required = false)
            String action,

            @RequestParam(required = false)
            Long actorUserId,

            @RequestParam(required = false)
            UUID operationId,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant changedFrom,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant changedTo,

            @AuthenticationPrincipal Jwt jwt
    ) {

        // Verify active admin
        adminAccessService.requireActiveAdmin(jwt);

        return auditService.getAudits(

                page,
                size,
                scope,
                topicId,
                configKey,
                action,
                actorUserId,
                operationId,
                changedFrom,
                changedTo
        );
    }


    // =====================================================
    // API 2: GET AUDIT DETAIL
    // =====================================================

    @GetMapping("/{auditId}")
    public GameConfigAuditDetailResponse getAuditDetail(

            @PathVariable Long auditId,

            @AuthenticationPrincipal Jwt jwt
    ) {

        // Verify active admin
        adminAccessService.requireActiveAdmin(jwt);

        return auditService.getAuditDetail(auditId);
    }
}