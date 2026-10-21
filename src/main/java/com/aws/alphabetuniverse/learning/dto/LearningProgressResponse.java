package com.aws.alphabetuniverse.learning.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LearningProgressResponse {

    private Long topicId;

    private Long furthestVocabId;

    private Integer furthestPosition;

    private Instant updatedAt;

    private String message;
}