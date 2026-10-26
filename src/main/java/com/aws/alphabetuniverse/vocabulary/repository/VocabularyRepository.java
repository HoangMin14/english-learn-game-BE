
package com.aws.alphabetuniverse.vocabulary.repository;

import com.aws.alphabetuniverse.vocabulary.entity.Vocabulary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VocabularyRepository
        extends JpaRepository<Vocabulary, Long> {

    List<Vocabulary> findByTopic_IdAndActiveTrueOrderByOrderNoAscIdAsc(Long topicId);

    Optional<Vocabulary> findFirstByTopic_IdAndActiveTrueAndOrderNoGreaterThanOrderByOrderNoAscIdAsc(Long topicId, Integer orderNo);

    List<Vocabulary> findByTopic_IdOrderByOrderNoAscIdAsc(Long topicId);

    boolean existsByTopic_IdAndWordIgnoreCase(Long topicId, String word);

    @Query("""
    SELECT COALESCE(MAX(v.orderNo), 0)
    FROM Vocabulary v
    WHERE v.topic.id = :topicId
    """)
    Integer findMaxOrderNoByTopicId(
            @Param("topicId") Long topicId
    );

    boolean existsByTopic_IdAndWordIgnoreCaseAndIdNot(
            Long topicId,
            String word,
            Long vocabularyId
    );

    List<Vocabulary> findAllByTopic_IdOrderByOrderNoAsc(
            Long topicId
    );
}
