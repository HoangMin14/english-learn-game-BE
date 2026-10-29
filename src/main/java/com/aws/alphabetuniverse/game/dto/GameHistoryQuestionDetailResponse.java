
package com.aws.alphabetuniverse.game.dto;

import java.util.List;

public record GameHistoryQuestionDetailResponse(
        Long vocabularyId,
        String word,
        String meaningVi,
        List<String> scrambledLetters,
        Integer topicPosition,
        boolean snapshotAvailable,
        String result,
        boolean correctlyAnswered,
        int attemptsUsed,
        List<GameHistoryAttemptDetailResponse> attempts
) {
}
