package com.aws.alphabetuniverse.topic.repository;

import com.aws.alphabetuniverse.topic.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {
     List<Topic> findByActiveTrueOrderByOrderNoAscIdAsc();
     Optional<Topic> findByIdAndActiveTrue(Long id);
}
