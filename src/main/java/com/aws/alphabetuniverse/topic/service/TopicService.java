package com.aws.alphabetuniverse.topic.service;

import com.aws.alphabetuniverse.topic.dto.TopicResponse;

import java.util.List;

public interface TopicService {

    public List<TopicResponse> getAllTopics();
    public TopicResponse getTopicById(Long topicId);
}