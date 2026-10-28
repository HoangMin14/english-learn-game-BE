package com.aws.alphabetuniverse.game.service;

import com.aws.alphabetuniverse.vocabulary.entity.Vocabulary;

import java.util.List;
import java.util.Set;

public interface CheckpointService {

    public boolean hasWonCheckpoint(
            Long userId,
            Long topicId,
            int batchStart,
            int batchEnd,
            List<Vocabulary> activeVocabularies
    );

    public Set<Set<Long>> getWonCheckpointVocabularyIdSets(Long userId, Long topicId);
}