package com.aws.alphabetuniverse.learning.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningResponse {

    private Long topicId;
    private String topicName;

    // NEW, LEARNING hoặc REVIEW
    private String mode;

    private Integer totalVocabularies;

    // order_no xa nhất đã lưu
    private Integer furthestPosition;

    // order_no của từ bắt đầu khi mở màn hình
    private Integer startPosition;

    private Long startVocabId;

    private boolean completed;

    private List<LearningVocabularyItem> vocabularies;
}