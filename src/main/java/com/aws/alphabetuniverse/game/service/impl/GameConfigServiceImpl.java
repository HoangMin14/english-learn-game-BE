
package com.aws.alphabetuniverse.game.service.impl;

import com.aws.alphabetuniverse.game.dto.EffectiveGameConfig;
import com.aws.alphabetuniverse.game.dto.admin.GameConfigAdminResponse;
import com.aws.alphabetuniverse.game.entity.GameConfig;
import com.aws.alphabetuniverse.game.repository.GameConfigRepository;
import com.aws.alphabetuniverse.game.service.GameConfigService;
import com.aws.alphabetuniverse.topic.repository.TopicRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GameConfigServiceImpl implements GameConfigService {

    private static final List<String> SUPPORTED_KEYS = List.of(
            "max_attempt_per_word",
            "checkpoint_pass_points",
            "score_enabled",
            "streak_enabled"
    );

    private final GameConfigRepository gameConfigRepository;
    private final TopicRepository topicRepository;

    @Override
    @Transactional(readOnly = true)
    public List<GameConfigAdminResponse> getGlobalConfigs() {
        return SUPPORTED_KEYS.stream()
                .map(key -> toGlobalResponse(requireGlobal(key)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GameConfigAdminResponse> getTopicConfigs(
            Long topicId
    ) {
        validateTopicExists(topicId);

        return SUPPORTED_KEYS.stream()
                .map(key -> toTopicResponse(topicId, key))
                .toList();
    }

    @Override
    @Transactional
    public GameConfigAdminResponse updateGlobalConfig(
            String configKey,
            String configValue
    ) {
        requireSupportedKey(configKey);

        String normalized =
                normalizeConfigValue(configKey, configValue);

        // Global luôn active.
        // Tắt điểm/streak bằng configValue = "false",
        // không tắt is_active của bản ghi global.
        gameConfigRepository.upsertGlobalConfig(
                configKey,
                normalized
        );

        // Đọc lại dữ liệu sau upsert.
        return toGlobalResponse(requireGlobal(configKey));
    }

    @Override
    @Transactional
    public GameConfigAdminResponse updateTopicConfig(
            Long topicId,
            String configKey,
            String configValue,
            boolean active
    ) {
        validateTopicExists(topicId);
        requireSupportedKey(configKey);

        String normalized =
                normalizeConfigValue(configKey, configValue);

        gameConfigRepository.upsertTopicConfig(
                topicId,
                configKey,
                normalized,
                active
        );

        return toTopicResponse(topicId, configKey);
    }

    @Override
    @Transactional
    public void resetTopicConfig(Long topicId) {
        validateTopicExists(topicId);

        gameConfigRepository.deleteTopicOverrides(topicId);
    }

    @Override
    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public EffectiveGameConfig getEffectiveConfigs(
            Long topicId
    ) {
        if (topicId == null || topicId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid topic ID"
            );
        }

        int maxAttempts = parseRuntimeInteger(
                "max_attempt_per_word",
                getEffectiveValue(topicId, "max_attempt_per_word"),
                1
        );

        int checkpointPoints = parseRuntimeInteger(
                "checkpoint_pass_points",
                getEffectiveValue(topicId, "checkpoint_pass_points"),
                0
        );

        boolean scoreEnabled = parseRuntimeBoolean(
                "score_enabled",
                getEffectiveValue(topicId, "score_enabled")
        );

        boolean streakEnabled = parseRuntimeBoolean(
                "streak_enabled",
                getEffectiveValue(topicId, "streak_enabled")
        );

        return new EffectiveGameConfig(
                maxAttempts,
                checkpointPoints,
                scoreEnabled,
                streakEnabled
        );
    }

    @Override
    @Transactional
    public void resetTopicConfig(
            Long topicId,
            String configKey
    ) {
        validateTopicExists(topicId);
        requireSupportedKey(configKey);

        // Không có override cũng không gây lỗi.
        // Global config không bị thay đổi.
        gameConfigRepository.deleteTopicOverride(
                topicId,
                configKey
        );
    }


    private String getEffectiveValue(
            Long topicId,
            String configKey
    ) {
        // 1. Ưu tiên cấu hình riêng topic nếu đang ACTIVE.
        Optional<GameConfig> override =
                gameConfigRepository.findByTopicIdAndConfigKey(
                        topicId,
                        configKey
                );

        if (override.isPresent() && isActive(override.get())) {
            return override.get().getConfigValue();
        }

        // 2. Chỉ dùng global nếu không có override ACTIVE.
        GameConfig global = requireGlobal(configKey);

        if (!isActive(global)) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Required global config is inactive: " + configKey
            );
        }

        return global.getConfigValue();
    }


    private GameConfig requireGlobal(String configKey) {
        return gameConfigRepository
                .findByTopicIdIsNullAndConfigKey(configKey)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Missing global config: " + configKey
                ));
    }

    private GameConfigAdminResponse toGlobalResponse(
            GameConfig global
    ) {
        boolean active = isActive(global);

        return new GameConfigAdminResponse(
                null,
                global.getConfigKey(),
                global.getConfigValue(),
                active,
                null,
                null,
                active ? global.getConfigValue() : null,
                active ? "GLOBAL" : "GLOBAL_INACTIVE"
        );
    }

    private GameConfigAdminResponse toTopicResponse(
            Long topicId,
            String configKey
    ) {
        GameConfig global = requireGlobal(configKey);

        GameConfig override = gameConfigRepository
                .findByTopicIdAndConfigKey(topicId, configKey)
                .orElse(null);

        boolean globalActive = isActive(global);

        Boolean overrideActive = override == null
                ? null
                : isActive(override);

        boolean useOverride =
                override != null && Boolean.TRUE.equals(overrideActive);

        String effectiveValue;

        if (useOverride) {
            effectiveValue = override.getConfigValue();
        } else if (globalActive) {
            effectiveValue = global.getConfigValue();
        } else {
            effectiveValue = null;
        }

        String source = useOverride
                ? "TOPIC"
                : globalActive ? "GLOBAL" : "GLOBAL_INACTIVE";

        return new GameConfigAdminResponse(
                topicId,
                configKey,
                global.getConfigValue(),
                globalActive,
                override == null ? null : override.getConfigValue(),
                overrideActive,
                effectiveValue,
                source
        );
    }

    private void validateTopicExists(Long topicId) {
        if (topicId == null || topicId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid topic ID"
            );
        }

        topicRepository.findById(topicId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Topic not found"
                ));
    }

    private void requireSupportedKey(String configKey) {
        if (configKey == null
                || !SUPPORTED_KEYS.contains(configKey)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported game configuration key"
            );
        }
    }

    private String normalizeConfigValue(
            String configKey,
            String rawValue
    ) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Configuration value is required"
            );
        }

        String value = rawValue.trim();

        return switch (configKey) {
            case "max_attempt_per_word" ->
                    normalizeInteger(configKey, value, 1);

            case "checkpoint_pass_points" ->
                    normalizeInteger(configKey, value, 0);

            case "score_enabled", "streak_enabled" -> {
                if (!"true".equalsIgnoreCase(value)
                        && !"false".equalsIgnoreCase(value)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            configKey + " must be true or false"
                    );
                }

                yield value.toLowerCase(Locale.ROOT);
            }

            default -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported game configuration key"
            );
        };
    }

    private String normalizeInteger(
            String configKey,
            String value,
            int minimum
    ) {
        try {
            int number = Integer.parseInt(value);

            if (number < minimum) {
                throw new NumberFormatException();
            }

            return Integer.toString(number);

        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid integer value for " + configKey
            );
        }
    }

    private int parseRuntimeInteger(
            String configKey,
            String rawValue,
            int minimum
    ) {
        try {
            if (rawValue == null || rawValue.isBlank()) {
                throw new NumberFormatException();
            }

            int number = Integer.parseInt(rawValue.trim());

            if (number < minimum) {
                throw new NumberFormatException();
            }

            return number;

        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Invalid stored integer config: " + configKey
            );
        }
    }

    private boolean parseRuntimeBoolean(
            String configKey,
            String rawValue
    ) {
        if ("true".equalsIgnoreCase(
                rawValue == null ? "" : rawValue.trim())) {
            return true;
        }

        if ("false".equalsIgnoreCase(
                rawValue == null ? "" : rawValue.trim())) {
            return false;
        }

        throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Invalid stored boolean config: " + configKey
        );
    }

    private boolean isActive(GameConfig config) {
        // Giả định GameConfig dùng Boolean isActive.
        // Nếu entity dùng boolean primitive, đổi thành config.isActive().
        return Boolean.TRUE.equals(config.getIsActive());
    }
}
