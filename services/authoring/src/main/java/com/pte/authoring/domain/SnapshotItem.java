package com.pte.authoring.domain;

import com.pte.authoring.domain.enums.PteSection;
import com.pte.authoring.domain.enums.PteTaskType;
import com.pte.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A frozen, self-contained copy of one question's content inside a snapshot.
 * Options are stored as a JSON string ({@code optionsJson}) so the item carries
 * everything needed to deliver + score the task with no reference back to the
 * mutable {@link Question}. Correct answers are retained here for the scoring
 * service; exam-delivery strips them before serving to a student.
 */
@Entity
@Table(name = "snapshot_items")
@Getter
@Setter
@NoArgsConstructor
public class SnapshotItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private ExamSnapshot snapshot;

    @Column(nullable = false)
    private UUID sourceQuestionPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PteTaskType pteTaskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PteSection section;

    @Column(nullable = false)
    private int orderIndex;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String promptText;

    @Column
    private UUID audioPromptRef;

    @Column
    private UUID imagePromptRef;

    @Column(columnDefinition = "text")
    private String referenceAnswerText;

    @Column(columnDefinition = "text")
    private String correctAnswerText;

    @Column
    private Integer minWordCount;

    @Column
    private Integer maxWordCount;

    @Column(columnDefinition = "text")
    private String optionsJson;
}
