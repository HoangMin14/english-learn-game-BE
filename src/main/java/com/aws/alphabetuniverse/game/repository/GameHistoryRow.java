package com.aws.alphabetuniverse.game.repository.projection;

import java.time.Instant;

public interface GameHistoryRow {

    Long getSessionId();

    Long getTopicId();

    String getTopicName();

    String getMode();

    String getStatus();

    Integer getScore();

    Boolean getWon();

    Instant getStartedAt();

    Instant getEndedAt();
}