package com.aws.alphabetuniverse.game.repository;

import com.aws.alphabetuniverse.game.entity.GameConfigAudit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface GameConfigAuditRepository
        extends JpaRepository<GameConfigAudit, Long> {

    @Query("""
        SELECT a
        FROM GameConfigAudit a
        WHERE (:scope IS NULL OR a.scope = :scope)

          AND (:topicId IS NULL OR a.topicId = :topicId)

          AND (:configKey IS NULL OR a.configKey = :configKey)

          AND (:action IS NULL OR a.action = :action)

          AND (:actorUserId IS NULL OR a.actorUserId = :actorUserId)

          AND (:operationId IS NULL OR a.operationId = :operationId)

          AND a.changedAt >= COALESCE(:changedFrom, a.changedAt)

          AND a.changedAt <= COALESCE(:changedTo, a.changedAt)
        """)
    Page<GameConfigAudit> searchAudits(

            @Param("scope")
            String scope,

            @Param("topicId")
            Long topicId,

            @Param("configKey")
            String configKey,

            @Param("action")
            String action,

            @Param("actorUserId")
            Long actorUserId,

            @Param("operationId")
            UUID operationId,

            @Param("changedFrom")
            Instant changedFrom,

            @Param("changedTo")
            Instant changedTo,

            Pageable pageable
    );
}