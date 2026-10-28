package com.aws.alphabetuniverse.progress.repository;

import com.aws.alphabetuniverse.progress.entity.UserProgress;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface UserProgressRepository
        extends JpaRepository<UserProgress, Long> {

    @Modifying
    @Query(value = """
    INSERT INTO public.user_progress AS up (
        user_id,
        vocab_id,
        completed,
        first_completed_at
    )
    VALUES (
        :userId,
        :vocabId,
        TRUE,
        :completedAt
    )
    ON CONFLICT (user_id, vocab_id)
    DO UPDATE SET
        completed = TRUE,
        first_completed_at = COALESCE(
            up.first_completed_at,
            EXCLUDED.first_completed_at
        )
    """, nativeQuery = true)
    int upsertCorrectAnswer(
            @Param("userId") Long userId,
            @Param("vocabId") Long vocabId,
            @Param("completedAt") LocalDateTime completedAt
    );
}