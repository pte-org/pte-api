package com.pte.attempt.domain;

import com.pte.attempt.domain.enums.AnswerStatus;
import com.pte.attempt.ResponseConfidence;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A student's response to one task of the frozen exam. {@code payload} is a free-form
 * answer string (selected option ids as CSV for MCQ, essay text for Write
 * Essay) — scoring (Phase 08) interprets it against the exam item's
 * correct-answer data. {@code expired=true} marks an auto-finalized empty
 * answer whose response window elapsed without a submission.
 */
@Entity
@Table(name = "attempt_answers", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"attempt_id", "item_public_id"})
}, indexes = {
        @Index(name = "idx_answers_attempt", columnList = "attempt_id")
})
@Getter
@Setter
@NoArgsConstructor
public class AttemptAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttempt attempt;

    /** The exam version item this answers; stable across the frozen exam's lifetime. */
    @Column(name = "item_public_id", nullable = false)
    private UUID itemPublicId;

    @Column(name = "item_order_index", nullable = false)
    private int itemOrderIndex;

    @Column(columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnswerStatus status = AnswerStatus.SUBMITTED;

    @Column(nullable = false)
    private boolean expired = false;

    /** Nullable for legacy/official submissions; practice may require it at its boundary. */
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private ResponseConfidence confidence;

    /**
     * Which independently persisted score counts for this answer: "AI" or "EXAMINER" (the scoring module's
     * vocabulary, kept as text because attempt does not depend on scoring). Null until the host chooses.
     */
    @Column(name = "selected_score_source", length = 16)
    private String selectedScoreSource;

    @Column(name = "selected_score_source_at")
    private Instant selectedScoreSourceAt;

    @Column(name = "selected_score_source_by")
    private UUID selectedScoreSourceByPublicId;

    /** The evaluation row the chosen source resolved to when it was chosen (the examiner score, if EXAMINER). */
    @Column(name = "selected_evaluation_public_id")
    private UUID selectedEvaluationPublicId;
}
