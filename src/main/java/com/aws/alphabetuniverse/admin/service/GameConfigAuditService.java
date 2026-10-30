package com.aws.alphabetuniverse.admin.service;

import com.aws.alphabetuniverse.game.dto.admin.GameConfigAuditDetailResponse;
import com.aws.alphabetuniverse.game.dto.admin.GameConfigAuditPageResponse;

import java.time.Instant;
import java.util.UUID;

public interface GameConfigAuditService {

    // API 1: Lấy danh sách lịch sử
    GameConfigAuditPageResponse getAudits(
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
    );

    // API 2: Xem chi tiết một bản ghi
    GameConfigAuditDetailResponse getAuditDetail(
            Long auditId
    );
}