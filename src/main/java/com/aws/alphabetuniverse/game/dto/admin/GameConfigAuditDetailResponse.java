package com.aws.alphabetuniverse.game.dto.admin;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record GameConfigAuditDetailResponse(

        Long id,

        UUID operationId,

        Long actorUserId,

        String scope,

        Long topicId,

        String configKey,

        String action,

        Map<String, Object> beforeData,

        Map<String, Object> afterData,

        Instant changedAt

) {
}