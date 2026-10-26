
package com.aws.alphabetuniverse.game.entity;

import com.aws.alphabetuniverse.topic.entity.Topic;
import com.aws.alphabetuniverse.user.entity.User;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "game_sessions")
public class GameSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @NotNull
    @Size(max = 20)
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @ColumnDefault("0")
    @Column(name = "score")
    private Integer score = 0;

    @ColumnDefault("false")
    @Column(name = "is_won")
    private Boolean won = false;

    @NotNull
    @Size(max = 20)
    @Column(name = "mode", nullable = false, length = 20)
    private String mode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "question_vocab_ids",
            nullable = false,
            columnDefinition = "jsonb"
    )
    private List<Long> questionVocabIds = new ArrayList<>();


    // Snapshot câu hỏi tại thời điểm tạo phiên.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_snapshot", columnDefinition = "jsonb")
    private List<GameQuestionSnapshot> questionSnapshot;

    // Vị trí đầu và cuối của nhóm checkpoint.
    @Column(name = "batch_start_position")
    private Integer batchStartPosition;

    @Column(name = "batch_end_position")
    private Integer batchEndPosition;

    // Số lượt thử được chốt khi tạo phiên.
    @Column(name = "max_attempts_per_word")
    private Integer maxAttemptsPerWord;


    @ColumnDefault("now()")
    @Column(name = "started_at")
    private Instant startedAt;

    @ColumnDefault("now()")
    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @Column(name = "ended_at")
    private Instant endedAt;
}
