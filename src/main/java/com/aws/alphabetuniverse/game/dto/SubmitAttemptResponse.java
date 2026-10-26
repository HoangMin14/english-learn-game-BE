package com.aws.alphabetuniverse.game.dto;

public record SubmitAttemptResponse(
        Long sessionId,
        Long vocabularyId,
        boolean correct,
        int attemptNumber,
        int remainingAttempts,
        boolean wordCompleted,
        boolean sessionCompleted,
        Boolean sessionWon,
        int pointsEarned,
        int totalScore,
        Long nextVocabularyId,
        String correctWord,
        Integer currentStreak
) {
}