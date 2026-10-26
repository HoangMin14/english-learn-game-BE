package com.aws.alphabetuniverse.game.repository;

import com.aws.alphabetuniverse.game.entity.GameSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
