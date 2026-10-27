
package com.aws.alphabetuniverse.game.dto;

import java.util.List;

public record GameResumeQuestionResponse(
        Long vocabularyId,
        String meaningVi,
        List<String> scrambledLetters,
        Integer topicPosition,
        String status,
        int attemptsUsed,
        boolean correctlyAnswered
) {
}
