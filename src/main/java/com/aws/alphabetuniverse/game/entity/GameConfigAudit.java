package com.aws.alphabetuniverse.game.entity;

import jakarta.persistence.*;

import lombok.Getter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "game_config_audits", schema = "public")
@Getter
@NoArgsConstructor
public class GameConfigAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_id", nullable = false)
    private UUID operationId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_cognito_sub")
    private String actorCognitoSub;

    @Column(name = "scope", nullable = false)
    private String scope;

    @Column(name = "topic_id")
    private Long topicId;

    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(name = "action", nullable = false)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data", columnDefinition = "jsonb")
    private Map<String, Object> beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_data", columnDefinition = "jsonb")
    private Map<String, Object> afterData;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
}