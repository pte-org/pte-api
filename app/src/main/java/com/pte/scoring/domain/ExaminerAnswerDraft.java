package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** Per-answer durable draft for unfinalized scoring work. */
@Entity
@Table(name = "examiner_answer_drafts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_examiner_draft_work_answer",
                        columnNames = {"work_id", "answer_public_id"})
        },
        indexes = {
                @Index(name = "idx_examiner_draft_tenant_answer", columnList = "tenant_id, answer_public_id")
        })
@Getter
@NoArgsConstructor
public class ExaminerAnswerDraft extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(name = "work_id", nullable = false)
    private Long workId;

    @Column(nullable = false)
    private UUID answerPublicId;

    private Integer score;

    @Column(length = 2000)
    private String comment;

    @Enumerated(EnumType.STRING)
    private ExaminerDecisionKind decisionKind;

    @Column(length = 128)
    private String expectedReviewVersion;

    @Column(length = 128)
    private String reviewedAiVersion;

    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private Instant savedAt;

    public ExaminerAnswerDraft(UUID tenantId, Long workId, UUID answerPublicId, Integer score,
            String comment, ExaminerDecisionKind decisionKind, String expectedReviewVersion,
            String reviewedAiVersion, Instant savedAt) {
        this.tenantId = tenantId;
        this.workId = workId;
        this.answerPublicId = answerPublicId;
        this.score = score;
        this.comment = comment;
        this.decisionKind = decisionKind;
        this.expectedReviewVersion = expectedReviewVersion;
        this.reviewedAiVersion = reviewedAiVersion;
        this.version = 1L;
        this.savedAt = savedAt;
    }

    public void update(Integer score, String comment, ExaminerDecisionKind decisionKind,
            String expectedReviewVersion, String reviewedAiVersion, Instant savedAt) {
        this.score = score;
        this.comment = comment;
        this.decisionKind = decisionKind;
        this.expectedReviewVersion = expectedReviewVersion;
        this.reviewedAiVersion = reviewedAiVersion;
        this.savedAt = savedAt;
        this.version++;
    }
}
