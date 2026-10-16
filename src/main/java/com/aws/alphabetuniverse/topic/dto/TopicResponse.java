package com.aws.alphabetuniverse.topic.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicResponse {

    private Long id;

    private String name;

    private String description;

    private String iconUrl;

    private Integer orderNo;

    private Boolean isActive;
}