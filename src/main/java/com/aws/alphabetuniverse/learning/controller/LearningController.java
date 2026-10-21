package com.aws.alphabetuniverse.learning.controller;

import com.aws.alphabetuniverse.learning.dto.LearningProgressRequest;
import com.aws.alphabetuniverse.learning.dto.LearningProgressResponse;
import com.aws.alphabetuniverse.learning.dto.LearningResponse;
import com.aws.alphabetuniverse.learning.service.LearningService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/topics")
@Tag(name = "learning")
@RequiredArgsConstructor
public class LearningController {

    private final LearningService learningService;

    @GetMapping("/{topicId}/learning")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<LearningResponse> getTopicLearning(
            @PathVariable Long topicId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        LearningResponse response =
                learningService.openTopicLearning(topicId, jwt);

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{topicId}/learning/progress")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<LearningProgressResponse> saveProgress(
            @PathVariable Long topicId,
            @Valid @RequestBody LearningProgressRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {

        LearningProgressResponse response =
                learningService.saveProgress(topicId, request, jwt);

        return ResponseEntity.ok(response);
    }


}