package com.aws.alphabetuniverse.learning.service;

import com.aws.alphabetuniverse.learning.dto.LearningProgressRequest;
import com.aws.alphabetuniverse.learning.dto.LearningProgressResponse;
import com.aws.alphabetuniverse.learning.dto.LearningResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface LearningService {
    public LearningResponse openTopicLearning(Long topicId, Jwt jwt);
    public LearningProgressResponse saveProgress(Long topicId, LearningProgressRequest request, Jwt jwt);

}
