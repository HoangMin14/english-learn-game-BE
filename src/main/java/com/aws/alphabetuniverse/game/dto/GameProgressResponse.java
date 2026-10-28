package com.aws.alphabetuniverse.game.dto;

import java.util.List;

public record GameProgressResponse(
        List<TopicProgressItemResponse> topics
) {
}