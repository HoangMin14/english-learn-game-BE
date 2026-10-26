
package com.aws.alphabetuniverse.streak.entity;

import com.aws.alphabetuniverse.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "user_streaks")
public class UserStreak {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "current_streak", nullable = false)
    private Integer currentStreak = 0;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "last_streak_date")
    private LocalDate lastStreakDate;

    @PrePersist
    protected void onCreate() {
        if (currentStreak == null) {
            currentStreak = 0;
        }

        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
