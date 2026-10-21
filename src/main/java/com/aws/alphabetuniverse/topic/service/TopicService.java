package com.aws.alphabetuniverse.topic.service;

import com.aws.alphabetuniverse.topic.dto.TopicRequest;
import com.aws.alphabetuniverse.topic.dto.TopicResponse;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public interface TopicService {

    public List<TopicResponse> getAllTopics();

    public TopicResponse getTopicById(Long topicId);

    public TopicResponse createTopic(TopicRequest request, Jwt jwt);

    public TopicResponse updateTopic(Long topicId, TopicRequest request, Jwt jwt);

    public void deleteTopic(Long topicId, Jwt jwt);

    public List<TopicResponse> getAllTopicsForAdmin(Jwt jwt);

    public TopicResponse restoreTopic(Long topicId, Jwt jwt);
}