
package com.aws.alphabetuniverse.vocabulary.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminVocabularyResponse {

    private Long id;
    private Long topicId;
    private String topicName;

    private String word;
    private String meaningVi;
    private String ipa;
    private String audioUrl;

    private Boolean active;
    private Integer orderNo;
}
