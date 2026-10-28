package com.aws.alphabetuniverse.game.repository;

import com.aws.alphabetuniverse.game.entity.GameSession;
import com.aws.alphabetuniverse.game.repository.projection.GameStatisticsSessionAggregate;
import com.aws.alphabetuniverse.leaderboard.repository.projection.WeeklyLeaderboardRow;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GameSessionRepository extends JpaRepository<GameSession,Long> {
    boolean existsByUser_IdAndTopic_IdAndStatus(
            Long userId,
            Long topicId,
            String status
    );
    List<GameSession> findAllByUser_IdAndTopic_IdAndModeOrderByStartedAtDesc(
            Long userId,
            Long topicId,
            String mode
    );

    boolean existsByUser_IdAndTopic_IdAndModeAndBatchStartPositionAndBatchEndPositionAndWonTrueAndStatus(
            Long userId,
            Long topicId,
            String mode,
            Integer batchStartPosition,
            Integer batchEndPosition,
            String status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM GameSession s WHERE s.id = :id")
    Optional<GameSession> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT s
    FROM GameSession s
    WHERE s.user.id = :userId
      AND s.topic.id = :topicId
      AND s.status = :status
    """)
    Optional<GameSession> findActiveSessionForUpdate(
            @Param("userId") Long userId,
            @Param("topicId") Long topicId,
            @Param("status") String status
    );

    @Query(value = """
    WITH totals AS (
        SELECT
            u.id AS user_id,
            NULLIF(BTRIM(u.display_name), '') AS display_name,
            SUM(s.score)::bigint AS total_score
        FROM public.game_sessions s
        JOIN public.users u
            ON u.id = s.user_id
        WHERE s.mode = 'CHECKPOINT'
          AND s.status = 'COMPLETED'
          AND s.is_won = TRUE
          AND s.score > 0
          AND s.ended_at >= :fromTime
          AND s.ended_at < :toTime
          AND LOWER(u.role) = 'learner'
          AND UPPER(u.status) = 'ACTIVE'
        GROUP BY u.id, u.display_name
    ),
    ranked AS (
        SELECT
            totals.user_id,
            totals.display_name,
            totals.total_score,
            RANK() OVER (
                ORDER BY totals.total_score DESC
            ) AS rank_value,
            ROW_NUMBER() OVER (
                ORDER BY totals.total_score DESC,
                         totals.user_id ASC
            ) AS position
        FROM totals
    )
    SELECT
        user_id AS "userId",
        display_name AS "displayName",
        total_score AS "totalScore",
        rank_value AS "rank",
        position AS "position"
    FROM ranked
    WHERE position <= :limit
       OR user_id = :currentUserId
    ORDER BY position
    """, nativeQuery = true)
    List<WeeklyLeaderboardRow> findWeeklyLeaderboard(
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime,
            @Param("currentUserId") Long currentUserId,
            @Param("limit") int limit
    );


    @Query(
            value = """
        SELECT
            s.id AS sessionId,
            t.id AS topicId,
            t.name AS topicName,
            s.mode AS mode,
            s.status AS status,
            s.score AS score,
            s.won AS won,
            s.startedAt AS startedAt,
            s.endedAt AS endedAt
        FROM GameSession s
        LEFT JOIN s.topic t
        WHERE s.user.id = :userId
          AND s.status IN :statuses
        ORDER BY s.startedAt DESC NULLS LAST, s.id DESC
        """,
            countQuery = """
        SELECT COUNT(s)
        FROM GameSession s
        WHERE s.user.id = :userId
          AND s.status IN :statuses
        """
    )
    Page<com.aws.alphabetuniverse.game.repository.projection.GameHistoryRow> findGameHistory(
            @Param("userId") Long userId,
            @Param("statuses") Collection<String> statuses,
            Pageable pageable
    );

    Optional<GameSession> findByUser_IdAndTopic_IdAndStatus(
            Long userId,
            Long topicId,
            String status
    );

    @Query("""
    SELECT
        COUNT(s) AS totalSessions,

        SUM(
            CASE
                WHEN s.status = 'COMPLETED'
                     AND s.won = true
                THEN 1
                ELSE 0
            END
        ) AS wonSessions,

        COALESCE(
            SUM(
                CASE
                    WHEN s.mode = 'CHECKPOINT'
                         AND s.status = 'COMPLETED'
                         AND s.won = true
                    THEN COALESCE(s.score, 0)
                    ELSE 0
                END
            ),
            0
        ) AS totalCheckpointScore

    FROM GameSession s
    WHERE s.user.id = :userId
    """)
    GameStatisticsSessionAggregate getStatisticsByUserId(
            @Param("userId") Long userId
    );

    List<GameSession> findAllByUser_IdAndTopic_IdAndModeAndStatusAndWonTrue(
            Long userId,
            Long topicId,
            String mode,
            String status
    );
}
