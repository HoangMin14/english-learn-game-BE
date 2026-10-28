package com.aws.alphabetuniverse.progress.entity;

import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.vocabulary.entity.Vocabulary;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "user_progress",
        schema = "public",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_user_progress_user_vocab",
                        columnNames = {"user_id", "vocab_id"}
                )
        }
)
public class UserProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vocab_id", nullable = false)
    private Vocabulary vocab;
    
    @ColumnDefault("false")
    @Column(name = "completed")
    private Boolean completed;

    @Column(name = "first_completed_at")
    private LocalDateTime firstCompletedAt;
}