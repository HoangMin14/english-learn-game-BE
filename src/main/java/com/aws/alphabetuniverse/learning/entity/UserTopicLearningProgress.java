package com.aws.alphabetuniverse.learning.entity;

import com.aws.alphabetuniverse.topic.entity.Topic;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.vocabulary.entity.Vocabulary;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_topic_learning_progress",
uniqueConstraints = @UniqueConstraint(
        name = "uq_user_topic_learning_progress",
        columnNames = {"user_id", "topic_id"}
))
public class UserTopicLearningProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "furthest_vocab_id", nullable = false)
    private Vocabulary furthestVocab;

    @NotNull
    @ColumnDefault("now()")
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;


}