package com.aws.alphabetuniverse.game.dto;

import java.util.List;

public record TopicProgressItemResponse(
        Long topicId,
        String topicName,

        Long activeSessionId,
        String activeSessionMode,

        int totalVocabularies,
        int furthestPosition,
        Long furthestVocabularyId,
        boolean reviewUnlocked,

        List<CheckpointProgressResponse> checkpoints
) {
}