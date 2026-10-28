package com.aws.alphabetuniverse.game.repository.projection;

public interface GameStatisticsAttemptAggregate {

    Long getTotalAttempts();

    Long getCorrectAttempts();
}