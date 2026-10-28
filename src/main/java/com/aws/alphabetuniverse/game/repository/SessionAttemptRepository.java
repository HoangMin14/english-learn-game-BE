
package com.aws.alphabetuniverse.game.repository;

import com.aws.alphabetuniverse.game.entity.SessionAttempt;
import com.aws.alphabetuniverse.game.repository.projection.GameStatisticsAttemptAggregate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SessionAttemptRepository
        extends JpaRepository<SessionAttempt, Long> {

    // Lấy danh sách lượt trả lời của một phiên game
    List<SessionAttempt> findBySession_IdOrderByIdAsc(
            Long sessionId
    );

    // Thống kê tổng lượt trả lời và tổng lượt trả lời đúng
    @Query("""
        SELECT
            COUNT(a) AS totalAttempts,
            COALESCE(
                SUM(
                    CASE
                        WHEN a.isCorrect = true THEN 1
                        ELSE 0
                    END
                ),
                0
            ) AS correctAttempts
        FROM SessionAttempt a
        JOIN a.session s
        WHERE s.user.id = :userId
        """)
    GameStatisticsAttemptAggregate getStatisticsByUserId(
            @Param("userId") Long userId
    );
}
