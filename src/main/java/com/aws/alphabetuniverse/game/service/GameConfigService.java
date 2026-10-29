package com.aws.alphabetuniverse.game.service;

import com.aws.alphabetuniverse.game.dto.EffectiveGameConfig;
import com.aws.alphabetuniverse.game.dto.admin.GameConfigAdminResponse;

import java.util.List;

public interface GameConfigService {

    List<GameConfigAdminResponse> getGlobalConfigs();

    List<GameConfigAdminResponse> getTopicConfigs(Long topicId);

    GameConfigAdminResponse updateGlobalConfig(
            String configKey,
            String configValue
    );

    GameConfigAdminResponse updateTopicConfig(
            Long topicId,
            String configKey,
            String configValue,
            boolean active
    );

    void resetTopicConfig(Long topicId);

    // Đọc cấu hình có hiệu lực cho luồng tạo game session.
    EffectiveGameConfig getEffectiveConfigs(Long topicId);

    void resetTopicConfig(
            Long topicId,
            String configKey
    );
}