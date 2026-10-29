
package com.aws.alphabetuniverse.game.repository;

import com.aws.alphabetuniverse.game.entity.GameConfig;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameConfigRepository
        extends JpaRepository<GameConfig, Long> {

    // Giữ lại nếu còn nơi khác đang dùng.
    Optional<GameConfig> findByConfigKeyAndIsActiveTrue(
            String configKey
    );

    // Cấu hình global: topic_id IS NULL.
    Optional<GameConfig> findByTopicIdIsNullAndConfigKey(
            String configKey
    );

    List<GameConfig> findByTopicIdIsNullOrderByConfigKeyAsc();

    // Cấu hình riêng topic.
    Optional<GameConfig> findByTopicIdAndConfigKey(
            Long topicId,
            String configKey
    );

    List<GameConfig> findByTopicIdOrderByConfigKeyAsc(
            Long topicId
    );

    // Upsert global.
    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query(value = """
        INSERT INTO public.game_configs
            (topic_id, config_key, config_value, is_active)
        VALUES
            (NULL, :configKey, :configValue, TRUE)
        ON CONFLICT (config_key) WHERE topic_id IS NULL
        DO UPDATE SET
            config_value = EXCLUDED.config_value,
            is_active = TRUE
        """, nativeQuery = true)
    int upsertGlobalConfig(
            @Param("configKey") String configKey,
            @Param("configValue") String configValue
    );

    // Upsert cấu hình topic.
    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query(value = """
        INSERT INTO public.game_configs
            (topic_id, config_key, config_value, is_active)
        VALUES
            (:topicId, :configKey, :configValue, :active)
        ON CONFLICT (topic_id, config_key)
            WHERE topic_id IS NOT NULL
        DO UPDATE SET
            config_value = EXCLUDED.config_value,
            is_active = EXCLUDED.is_active
        """, nativeQuery = true)
    int upsertTopicConfig(
            @Param("topicId") Long topicId,
            @Param("configKey") String configKey,
            @Param("configValue") String configValue,
            @Param("active") boolean active
    );

    // Reset bốn cấu hình được hỗ trợ ở phiên bản đầu.
    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query(value = """
        DELETE FROM public.game_configs
        WHERE topic_id = :topicId
          AND config_key IN (
              'max_attempt_per_word',
              'checkpoint_pass_points',
              'score_enabled',
              'streak_enabled'
          )
        """, nativeQuery = true)
    int deleteTopicOverrides(
            @Param("topicId") Long topicId
    );

    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query(value = """
    DELETE FROM public.game_configs
    WHERE topic_id = :topicId
      AND config_key = :configKey
    """, nativeQuery = true)
    int deleteTopicOverride(
            @Param("topicId") Long topicId,
            @Param("configKey") String configKey
    );
}
