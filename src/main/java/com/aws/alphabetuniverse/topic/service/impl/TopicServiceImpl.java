package com.aws.alphabetuniverse.topic.service.impl;

import com.aws.alphabetuniverse.topic.dto.TopicResponse;
import com.aws.alphabetuniverse.topic.entity.Topic;
import com.aws.alphabetuniverse.topic.repository.TopicRepository;
import com.aws.alphabetuniverse.topic.service.TopicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.aws.alphabetuniverse.common.exception.ResourceNotFoundException;


import java.util.List;

@Service
public class TopicServiceImpl implements TopicService {
    @Autowired
    private TopicRepository topicRepository;

    private TopicResponse toResponse(Topic topic) {

        return TopicResponse.builder()
                .id(topic.getId())
                .name(topic.getName())
                .description(topic.getDescription())
                .iconUrl(topic.getIconUrl())
                .orderNo(topic.getOrderNo())
                .isActive(topic.getActive())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicResponse> getAllTopics() {

        return topicRepository
                .findByActiveTrueOrderByOrderNoAscIdAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TopicResponse getTopicById(Long topicId) {
        Topic topic = topicRepository
                .findByIdAndActiveTrue(topicId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic not found"));
        return toResponse(topic);
    }


}
