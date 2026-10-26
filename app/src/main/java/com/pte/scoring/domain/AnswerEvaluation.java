package com.pte.scoring.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import org.hibernate.annotations.DiscriminatorFormula;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One independently persisted score of one submitted answer. An answer has at most one row per source — the
 * automatic one ({@link AiEvaluation}: objective rules or AI, with its provider and detail), the examiner's
 * ({@link ExaminerEvaluation}: a draft that becomes an immutable submitted score) — so "at most one AI and at
 * most one examiner score" is the table's unique key, not a service check. Which of them counts is not stored
 * here but on the answer ({@code AttemptAnswer.selectedScoreSource}). The answer's content is never copied: it is
 * read from {@code attempt} when needed.
 */
@Entity
@Table(name = "answer_evaluations", uniqueConstraints = {
        @UniqueConstraint(name = "uq_answer_evaluation_source", columnNames = {"answer_public_id", "source"})
}, indexes = {
        @Index(name = "idx_answer_evaluations_session", columnList = "tenant_id, session_public_id"),
        @Index(name = "idx_answer_evaluations_attempt", columnList = "tenant_id, attempt_public_id")
})
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorFormula("source")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AnswerEvaluation extends BaseEntity {

    /** The kind of evaluation, written by the subclass; it is also the discriminator Hibernate reads back. */
    @Column(name = "source", nullable = false, updatable = false, length = 16)
    private String source;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "session_public_id", nullable = false, updatable = false)
    private UUID sessionPublicId;

    @Column(name = "attempt_public_id", nullable = false, updatable = false)
    private UUID attemptPublicId;

    @Column(name = "answer_public_id", nullable = false, updatable = false)
    private UUID answerPublicId;

    /** 0-100; null until the evaluation has a result. */
    @Column(name = "score")
    private Integer score;

    @Column(name = "scored_at")
    private Instant scoredAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    protected AnswerEvaluation(String source, UUID tenantId, UUID sessionPublicId, UUID attemptPublicId,
            UUID answerPublicId) {
        this.source = source;
        this.tenantId = tenantId;
        this.sessionPublicId = sessionPublicId;
        this.attemptPublicId = attemptPublicId;
        this.answerPublicId = answerPublicId;
    }

    protected void recordScore(Integer score, Instant scoredAt) {
        this.score = score;
        this.scoredAt = scoredAt;
    }
}
