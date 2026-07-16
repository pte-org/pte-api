package com.aptis.modules.questionbank.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import com.aptis.common.domain.BaseEntity;
import com.aptis.modules.questionbank.domain.enums.DifficultyLevel;
import com.aptis.modules.questionbank.domain.enums.PteTaskType;
import com.aptis.modules.questionbank.domain.enums.QuestionSource;
import com.aptis.modules.questionbank.domain.enums.QuestionStatus;
import com.aptis.modules.questionbank.domain.enums.QuestionType;
import com.aptis.modules.questionbank.domain.enums.Skill;

@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class Question extends BaseEntity {

    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "parent_id")
    private Long parentId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "options", columnDefinition = "text[]")
    private List<String> options;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "correct_answers", columnDefinition = "text[]")
    private List<String> correctAnswers;

    @Column(name = "is_current", nullable = false)
    private Boolean isCurrent = true;

    @Column(name = "is_immutable", nullable = false)
    private Boolean isImmutable = false;

    /**
     * @deprecated APTIS-era single-skill tag. PTE questions no longer map to
     * exactly one skill — use {@link #pteTaskType} with
     * {@link PteTaskTypeSkillMapping#skillsFor(PteTaskType)} instead. Column
     * stays nullable-in-practice for PTE questions during the transition;
     * legacy APTIS questions keep their value (archived, not migrated).
     */
    @Deprecated
    @Enumerated(EnumType.STRING)
    @Column
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(name = "pte_task_type")
    private PteTaskType pteTaskType;

    /** Reference/model answer used as scoring context for AI-scored Speaking/Writing tasks. */
    @Column(name = "reference_answer_text", columnDefinition = "text")
    private String referenceAnswerText;

    @Column(name = "min_word_count")
    private Integer minWordCount;

    @Column(name = "max_word_count")
    private Integer maxWordCount;

    @Column(nullable = false)
    private Integer part;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuestionType questionType;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(columnDefinition = "text")
    private String instruction;

    @Column(name = "score_weight", nullable = false)
    private Float scoreWeight = 1.0f;

    @Column(columnDefinition = "text")
    private String explanation;

    @Column(name = "time_limit")
    private Integer timeLimit;

    @Column(name = "prep_time")
    private Integer prepTime;

    @Column(name = "max_play_count")
    private Integer maxPlayCount;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "asset_ids", columnDefinition = "uuid[]")
    private List<UUID> assetIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level", nullable = false)
    private DifficultyLevel difficultyLevel;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "topic_tags", nullable = false, columnDefinition = "text[]")
    private List<String> topicTags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionStatus status = QuestionStatus.DRAFT;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    /**
     * Null = Vendor-owned/shared content, visible to every tenant.
     * Non-null = Host-authored content, scoped to that tenant only.
     */
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionSource source = QuestionSource.VENDOR;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "audio_asset_id")
    private UUID audioAssetId;


    @PrePersist
    protected void prePersist() {
        super.prePersist();
        if (topicTags == null) {
            topicTags = List.of();
        }
    }
}
