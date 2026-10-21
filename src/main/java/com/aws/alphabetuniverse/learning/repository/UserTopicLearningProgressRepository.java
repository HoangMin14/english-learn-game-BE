package com.aws.alphabetuniverse.learning.repository;

import com.aws.alphabetuniverse.learning.entity.UserTopicLearningProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserTopicLearningProgressRepository
        extends JpaRepository<UserTopicLearningProgress, Long> {

    Optional<UserTopicLearningProgress>
    findByUser_IdAndTopic_Id(Long userId, Long topicId);
}