package com.aws.alphabetuniverse.game.repository;

import com.aws.alphabetuniverse.game.entity.SessionAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionAttemptRepository
        extends JpaRepository<SessionAttempt, Long> {

    List<SessionAttempt> findBySession_IdOrderByIdAsc(Long sessionId);
}