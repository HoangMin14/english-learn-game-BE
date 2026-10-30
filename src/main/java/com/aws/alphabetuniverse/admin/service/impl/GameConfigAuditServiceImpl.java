package com.aws.alphabetuniverse.admin.service.impl;

import com.aws.alphabetuniverse.admin.service.GameConfigAuditService;

import com.aws.alphabetuniverse.game.dto.admin.GameConfigAuditDetailResponse;
import com.aws.alphabetuniverse.game.dto.admin.GameConfigAuditListItemResponse;
import com.aws.alphabetuniverse.game.dto.admin.GameConfigAuditPageResponse;

import com.aws.alphabetuniverse.game.entity.GameConfigAudit;
import com.aws.alphabetuniverse.game.repository.GameConfigAuditRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GameConfigAuditServiceImpl
        implements GameConfigAuditService {

    private final GameConfigAuditRepository auditRepository;

    private static final Set<String> VALID_SCOPES =
            Set.of("GLOBAL", "TOPIC");

    private static final Set<String> VALID_ACTIONS =
            Set.of("INSERT", "UPDATE", "DELETE");


    // =====================================================
    // API 1: GET AUDIT HISTORY
    // =====================================================

    @Override
    public GameConfigAuditPageResponse getAudits(

            int page,
            int size,
            String scope,
            Long topicId,
            String configKey,
            String action,
            Long actorUserId,
            UUID operationId,
            Instant changedFrom,
            Instant changedTo

    ) {

        // 1. Validate pagination

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be greater than or equal to 0"
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Size must be between 1 and 100"
            );
        }


        // 2. Normalize filters

        scope = normalizeEnumFilter(
                scope,
                VALID_SCOPES,
                "scope"
        );

        action = normalizeEnumFilter(
                action,
                VALID_ACTIONS,
                "action"
        );


        if (topicId != null && topicId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid topicId"
            );
        }

        if (actorUserId != null && actorUserId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid actorUserId"
            );
        }


        configKey = normalizeTextFilter(configKey);


        // 3. Validate date range

        if (changedFrom != null
                && changedTo != null
                && changedFrom.isAfter(changedTo)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "changedFrom must be before changedTo"
            );
        }

        long offset = (long) page * size;

        if (offset > Integer.MAX_VALUE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page is too large"
            );
        }

        // 4. Create pageable

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("changedAt"),
                        Sort.Order.desc("id")
                )
        );


        // 5. Query database

        Page<GameConfigAudit> auditPage =
                auditRepository.searchAudits(

                        scope,
                        topicId,
                        configKey,
                        action,
                        actorUserId,
                        operationId,
                        changedFrom,
                        changedTo,
                        pageable
                );


        // 6. Convert Entity to DTO

        List<GameConfigAuditListItemResponse> content =
                auditPage.getContent()
                        .stream()
                        .map(this::toListItemResponse)
                        .toList();


        // 7. Return pagination response

        return new GameConfigAuditPageResponse(
                content,
                auditPage.getNumber(),
                auditPage.getSize(),
                auditPage.getTotalElements(),
                auditPage.getTotalPages()
        );
    }


    // =====================================================
    // API 2: GET AUDIT DETAIL
    // =====================================================

    @Override
    public GameConfigAuditDetailResponse getAuditDetail(
            Long auditId
    ) {

        // 1. Validate ID

        if (auditId == null || auditId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid auditId"
            );
        }


        // 2. Find audit

        GameConfigAudit audit = auditRepository
                .findById(auditId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Game config audit not found"
                ));


        // 3. Convert to DTO

        return toDetailResponse(audit);
    }


    // =====================================================
    // HELPER METHODS
    // =====================================================

    private String normalizeEnumFilter(
            String value,
            Set<String> allowedValues,
            String fieldName
    ) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value
                .trim()
                .toUpperCase(Locale.ROOT);

        if (!allowedValues.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid " + fieldName
            );
        }

        return normalized;
    }


    private String normalizeTextFilter(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }


    // Extract config_value from JSONB Map

    private String extractConfigValue(
            Map<String, Object> data
    ) {

        if (data == null) {
            return null;
        }

        Object value = data.get("config_value");

        return value == null
                ? null
                : value.toString();
    }


    // Convert to list response

    private GameConfigAuditListItemResponse toListItemResponse(
            GameConfigAudit audit
    ) {

        return new GameConfigAuditListItemResponse(

                audit.getId(),

                audit.getOperationId(),

                audit.getActorUserId(),

                audit.getScope(),

                audit.getTopicId(),

                audit.getConfigKey(),

                audit.getAction(),

                extractConfigValue(audit.getBeforeData()),

                extractConfigValue(audit.getAfterData()),

                audit.getChangedAt()
        );
    }


    // Convert to detail response

    private GameConfigAuditDetailResponse toDetailResponse(
            GameConfigAudit audit
    ) {

        return new GameConfigAuditDetailResponse(

                audit.getId(),

                audit.getOperationId(),

                audit.getActorUserId(),

                audit.getScope(),

                audit.getTopicId(),

                audit.getConfigKey(),

                audit.getAction(),

                audit.getBeforeData(),

                audit.getAfterData(),

                audit.getChangedAt()
        );
    }
}