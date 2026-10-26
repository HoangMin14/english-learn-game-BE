package com.aws.alphabetuniverse.game.service;

import com.aws.alphabetuniverse.vocabulary.entity.Vocabulary;

import java.util.List;

public interface CheckpointService {

    boolean hasWonCheckpoint(
            Long userId,
            Long topicId,
            int batchStart,
            int batchEnd,
            List<Vocabulary> activeVocabularies
    );
}