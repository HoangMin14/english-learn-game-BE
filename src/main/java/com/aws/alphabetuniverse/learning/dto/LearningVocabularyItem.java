package com.aws.alphabetuniverse.learning.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningVocabularyItem {

    private Long id;
    private String word;
    private String meaningVi;
    private String ipa;
    private String audioUrl;
    private Integer orderNo;
}