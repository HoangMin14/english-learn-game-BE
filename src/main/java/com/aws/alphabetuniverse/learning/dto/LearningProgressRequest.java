package com.aws.alphabetuniverse.learning.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LearningProgressRequest {

    @NotNull(message = "Vocabulary ID is required")
    @Positive(message = "Vocabulary ID must be positive")
    private Long vocabId;
}