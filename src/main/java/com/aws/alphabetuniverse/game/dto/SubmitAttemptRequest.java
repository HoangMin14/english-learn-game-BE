package com.aws.alphabetuniverse.game.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SubmitAttemptRequest(

        @NotNull(message = "Vocabulary ID is required")
        Long vocabularyId,

        @NotEmpty(message = "Submitted letters are required")
        @Size(max = 100, message = "Too many letters")
        List<
                @Pattern(
                        regexp = "(?i)^[a-z'-]$",
                        message = "Each item must be one English letter or a supported separator"
                )
                        String
                > submittedLetters,

        @Min(value = 0, message = "Time must not be negative")
        Integer timeMs

) {
}