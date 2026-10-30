package com.aws.alphabetuniverse.game.dto.admin;

import java.time.Instant;
import java.util.UUID;

public record GameConfigAuditListItemResponse(

        Long id,

        UUID operationId,

        Long actorUserId,

        String scope,

        Long topicId,

        String configKey,

        String action,

        String oldValue,

        String newValue,

        Instant changedAt

) {
}