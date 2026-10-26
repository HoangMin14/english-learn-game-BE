
package com.aws.alphabetuniverse.streak.repository;

import com.aws.alphabetuniverse.streak.entity.UserStreak;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface UserStreakRepository
        extends JpaRepository<UserStreak, Long> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserStreak> findById(Long id);
}
