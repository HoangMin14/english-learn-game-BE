package com.aws.alphabetuniverse.vocabulary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record CreateVocabularyRequest(

        @NotBlank(message = "English word is required")
        @Size(max = 100, message = "Word must not exceed 100 characters")
        String word,

        @NotBlank(message = "Vietnamese meaning is required")
        @Size(max = 255, message = "Meaning must not exceed 255 characters")
        String meaningVi

) {
}