
package com.aws.alphabetuniverse.vocabulary.entity;

import com.aws.alphabetuniverse.topic.entity.Topic;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "vocabularies")
public class Vocabulary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @Size(max = 100)
    @NotNull
    @Column(name = "word", nullable = false, length = 100)
    private String word;

    @Size(max = 255)
    @NotNull
    @Column(name = "meaning_vi", nullable = false, length = 255)
    private String meaningVi;

    @Size(max = 100)
    @Column(name = "ipa", length = 100)
    private String ipa;

    @Column(name = "audio_url", columnDefinition = "text")
    private String audioUrl;

    @Builder.Default
    @ColumnDefault("true")
    @Column(name = "is_active")
    private Boolean active = true;

    @NotNull
    @Column(name = "order_no", nullable = false)
    private Integer orderNo;
}
