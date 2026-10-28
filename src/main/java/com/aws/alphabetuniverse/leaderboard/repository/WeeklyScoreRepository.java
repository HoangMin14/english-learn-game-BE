
package com.aws.alphabetuniverse.leaderboard.repository;

import com.aws.alphabetuniverse.leaderboard.entity.WeeklyScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface WeeklyScoreRepository
        extends JpaRepository<WeeklyScore, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
        INSERT INTO public.weekly_scores (
            user_id,
            week_start,
            total_score
        )
        SELECT
            :userId,
            :weekStart,
            COALESCE(SUM(s.score), 0)
        FROM public.game_sessions s
        WHERE s.user_id = :userId
          AND s.mode = 'CHECKPOINT'
          AND s.status = 'COMPLETED'
          AND s.is_won = TRUE
          AND s.score > 0
          AND s.ended_at >= :fromTime
          AND s.ended_at < :toTime
        ON CONFLICT (user_id, week_start)
        DO UPDATE SET
            total_score = EXCLUDED.total_score
        """, nativeQuery = true)
    int synchronizeWeek(
            @Param("userId") Long userId,
            @Param("weekStart") LocalDate weekStart,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime
    );
}
