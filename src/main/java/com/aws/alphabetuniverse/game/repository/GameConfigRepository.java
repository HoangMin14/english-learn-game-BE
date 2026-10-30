
package com.aws.alphabetuniverse.game.repository;

import com.aws.alphabetuniverse.game.entity.GameConfig;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        INSERT INTO public.game_configs AS current_config
            (topic_id, config_key, config_value, description, is_active)
        VALUES
            (NULL, :configKey, :configValue, :description, TRUE)

        ON CONFLICT (config_key)
        WHERE topic_id IS NULL

        DO UPDATE SET
            config_value = EXCLUDED.config_value,
            description = EXCLUDED.description,
            is_active = TRUE
        """, nativeQuery = true)
    int upsertGlobalConfig(
            @Param("configKey") String configKey,
            @Param("configValue") String configValue,
            @Param("description") String description
    );

    // Upsert cấu hình topic.
    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query(value = """
    INSERT INTO public.game_configs AS current_config
    (
        topic_id,
        config_key,
        config_value,
        description,
        is_active
    )
    VALUES (
        :topicId,
        :configKey,
        :configValue,
        :description,
        :active
    )
    ON CONFLICT (topic_id, config_key)
    WHERE topic_id IS NOT NULL
    DO UPDATE SET
        config_value = EXCLUDED.config_value,
        description = EXCLUDED.description,
        is_active = EXCLUDED.is_active
    """, nativeQuery = true)
    int upsertTopicConfig(
            @Param("topicId") Long topicId,
            @Param("configKey") String configKey,
            @Param("configValue") String configValue,
            @Param("description") String description,
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

    @Query(value = """
    SELECT
        set_config(
            'app.audit_actor_user_id',
            CAST(:actorUserId AS TEXT),
            true
        )
        || '|'
        ||
        set_config(
            'app.audit_actor_cognito_sub',
            :actorCognitoSub,
            true
        )
        || '|'
        ||
        set_config(
            'app.audit_operation_id',
            CAST(:operationId AS TEXT),
            true
        )
    """, nativeQuery = true)
    String setAuditContext(

            @Param("actorUserId")
            Long actorUserId,

            @Param("actorCognitoSub")
            String actorCognitoSub,

            @Param("operationId")
            UUID operationId
    );
}
