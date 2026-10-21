package com.aws.alphabetuniverse.topic.repository;

import com.aws.alphabetuniverse.topic.entity.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TopicRepository extends JpaRepository<Topic, Long> {
     List<Topic> findByActiveTrueOrderByOrderNoAscIdAsc();
     Optional<Topic> findByIdAndActiveTrue(Long id);
     boolean existsByNameIgnoreCase(String name);
     boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
     @Query("SELECT COALESCE(MAX(t.orderNo), 0) FROM Topic t")
     Integer findMaxOrderNo();
}
