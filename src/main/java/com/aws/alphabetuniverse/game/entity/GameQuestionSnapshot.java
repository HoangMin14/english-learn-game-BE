package com.aws.alphabetuniverse.game.entity;

import java.util.List;

public record GameQuestionSnapshot(
        Long vocabularyId,
        String word,
        String meaningVi,
        List<String> scrambledLetters,
        Integer topicPosition
) {
}