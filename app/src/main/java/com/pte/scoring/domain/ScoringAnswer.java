package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.shared.domain.BaseEntity;
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
 * scoring's own work-state copy of one submitted answer, pulled from {@code
 * attempt}'s canonical {@code AttemptAnswer}/{@code PinnedItem} on demand
 * (Phase 08 — no live event feed, no runtime dependency beyond the pull
 * itself). Owns nothing attempt considers source-of-truth; this is a
 * bounded-context copy scoped to what grading needs, same category of
 * deliberate copy as attempt's own pinned snapshot.
 */
@Entity
@Table(name = "scoring_answers", indexes = {
        @Index(name = "idx_scoring_answers_session", columnList = "session_public_id"),
        @Index(name = "idx_scoring_answers_attempt", columnList = "attempt_public_id"),
        @Index(name = "idx_scoring_answers_status", columnList = "status"),
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

    /** Copied once at ingest time from the submitting attempt's pinned snapshot (spec FR-07/FR-14) — resolves scoringMethod via {@code ScoringMethodResolver}, never re-derived from a hardcoded task-type catalog. */
    @Column(nullable = false)
    private UUID scoreTemplatePublicId;

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
     * A host's own independent score — parallel to {@link #rawScore}, never
     * derived from it and never gating it. Which of the two counts as
     * "official" for student-facing reports is undecided (out of scope here).
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
