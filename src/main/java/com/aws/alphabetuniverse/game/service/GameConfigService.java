package com.aws.alphabetuniverse.game.service;

import com.aws.alphabetuniverse.game.dto.EffectiveGameConfig;
import com.aws.alphabetuniverse.game.dto.admin.GameConfigAdminResponse;
import com.aws.alphabetuniverse.game.dto.admin.GameConfigAuditContext;

import java.util.List;

public interface GameConfigService {

    List<GameConfigAdminResponse> getGlobalConfigs();

    List<GameConfigAdminResponse> getTopicConfigs(Long topicId);

    GameConfigAdminResponse updateGlobalConfig(
            String configKey,
            String configValue,
            String description,
            GameConfigAuditContext auditContext
    );

    GameConfigAdminResponse updateTopicConfig(
            Long topicId,
            String configKey,
            String configValue,
            String description,
            boolean active,
            GameConfigAuditContext auditContext
    );

    void resetTopicConfig(
            Long topicId,
            GameConfigAuditContext auditContext
    );

    // Đọc cấu hình có hiệu lực cho luồng tạo game session.
    EffectiveGameConfig getEffectiveConfigs(Long topicId);

    void resetTopicConfig(
            Long topicId,
            String configKey,
            GameConfigAuditContext auditContext
    );
}