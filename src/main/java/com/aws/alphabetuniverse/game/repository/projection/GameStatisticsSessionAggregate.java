package com.aws.alphabetuniverse.game.repository.projection;

public interface GameStatisticsSessionAggregate {

    Long getTotalSessions();

    Long getWonSessions();

    Long getTotalCheckpointScore();
}