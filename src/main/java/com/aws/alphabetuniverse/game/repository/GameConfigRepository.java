package com.aws.alphabetuniverse.game.repository;

import com.aws.alphabetuniverse.game.entity.GameConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameConfigRepository
        extends JpaRepository<GameConfig, Long> {

    Optional<GameConfig> findByConfigKeyAndIsActiveTrue(
            String configKey
    );
}