
package com.aws.alphabetuniverse.vocabulary.repository;

import com.aws.alphabetuniverse.vocabulary.entity.Vocabulary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VocabularyRepository
        extends JpaRepository<Vocabulary, Long> {

    List<Vocabulary> findByTopic_IdAndActiveTrueOrderByOrderNoAscIdAsc(Long topicId);

    Optional<Vocabulary> findFirstByTopic_IdAndActiveTrueAndOrderNoGreaterThanOrderByOrderNoAscIdAsc(Long topicId, Integer orderNo);

    List<Vocabulary> findByTopic_IdOrderByOrderNoAscIdAsc(Long topicId);
}
