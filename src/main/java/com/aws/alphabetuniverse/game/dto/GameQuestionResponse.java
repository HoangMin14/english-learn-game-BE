package com.aws.alphabetuniverse.game.dto;

import java.util.List;

public record GameQuestionResponse(Long vocabularyId, String meaningVi, List<String> scrambledLetters) {
}
