
package com.aws.alphabetuniverse.game.entity;

import com.aws.alphabetuniverse.vocabulary.entity.Vocabulary;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "session_attempts")
public class SessionAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private GameSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vocab_id", nullable = false)
    private Vocabulary vocabulary;

    @NotBlank
    @Column(
            name = "submitted_letters",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String submittedLetters;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect = false;

    @Column(name = "points", nullable = false)
    private Integer points = 0;

    @Column(name = "time_ms")
    private Integer timeMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }

        if (isCorrect == null) {
            isCorrect = false;
        }

        if (points == null) {
            points = 0;
        }
    }
}
