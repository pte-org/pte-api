package com.pte.scoring.domain;

import com.pte.common.domain.BaseEntity;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * scoring's own projection of one submitted answer, built from exam-delivery's
 * {@code AnswerSubmitted} event (event-carries-state — no runtime dependency on
 * exam-delivery). Owns nothing exam-delivery considers source-of-truth; this is
 * a read-optimized copy scoped to what grading needs.
 */
@Entity
@Table(name = "scoring_answers", indexes = {
        @Index(name = "idx_scoring_answers_session", columnList = "session_public_id"),
        @Index(name = "idx_scoring_answers_attempt", columnList = "attempt_public_id"),
        @Index(name = "idx_scoring_answers_status", columnList = "status"),
        // Host review list (quang-host-answer-review Phase 3) — every review query
        // filters by tenant first, optionally narrows by session/status, ordered
        // newest-first; covers that access path without a full-table scan.
        @Index(name = "idx_scoring_answers_tenant_review", columnList = "tenant_id, session_public_id, status, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ScoringAnswer extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID answerPublicId;

    @Column(nullable = false)
    private UUID attemptPublicId;

    @Column(nullable = false)
    private UUID pinnedItemPublicId;

    @Column(nullable = false)
    private UUID sessionPublicId;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String taskType;

    @Column(columnDefinition = "text")
    private String payload;

    @Column(columnDefinition = "text")
    private String correctAnswerText;

    @Column(columnDefinition = "text")
    private String optionsJson;

    @Column(nullable = false)
    private boolean expired;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScoringAnswerStatus status = ScoringAnswerStatus.PENDING;

    @Column
    private Integer rawScore;

    @Column
    private Instant scoredAt;

    /**
     * A host's own independent score (quang-host-answer-review Phase 5) —
     * parallel to {@link #rawScore}, never derived from it and never gating
     * it. Settable at any {@link #status}, for future AI-vs-teacher
     * comparison statistics; which of the two counts as "official" for
     * student-facing reports is explicitly undecided (out of scope here).
     */
    @Column
    private Integer teacherScore;

    @Column
    private Instant teacherScoredAt;

    public void markScored(int rawScore) {
        this.status = ScoringAnswerStatus.SCORED;
        this.rawScore = rawScore;
        this.scoredAt = Instant.now();
    }
}
