package com.aws.alphabetuniverse.topic.controller;

import com.aws.alphabetuniverse.topic.dto.TopicResponse;
import com.aws.alphabetuniverse.topic.service.TopicService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/topics")
@Tag(name = "topic")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
public class TopicController {

    private final TopicService topicService;

    @GetMapping
    public ResponseEntity<List<TopicResponse>> getAllTopics() {
        return ResponseEntity.ok(topicService.getAllTopics());
    }

    @GetMapping("/{topicId}")
    public ResponseEntity<TopicResponse> getTopicById(@PathVariable Long topicId) {
        return ResponseEntity.ok(topicService.getTopicById(topicId));
    }
}