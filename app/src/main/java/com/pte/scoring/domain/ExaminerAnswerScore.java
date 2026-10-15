package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable 0-100 Examiner score; distinct from AI rawScore and Host teacherScore. */
@Entity
@Table(name = "examiner_answer_scores",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_examiner_answer_score_answer", columnNames = "answer_public_id"),
                @UniqueConstraint(name = "uq_examiner_score_source_reference",
                        columnNames = {"public_id", "answer_public_id", "tenant_id", "session_public_id", "attempt_public_id"})
        },
        indexes = {
                @Index(name = "idx_examiner_score_attempt", columnList = "tenant_id, session_public_id, attempt_public_id"),
                @Index(name = "idx_examiner_score_examiner", columnList = "tenant_id, session_public_id, examiner_public_id")
        })
@Getter
@NoArgsConstructor
public class ExaminerAnswerScore extends BaseEntity {

    @Column(nullable = false)
    private UUID answerPublicId;

    @Column(nullable = false)
    private UUID attemptPublicId;

    @Column(nullable = false)
    private UUID sessionPublicId;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID examinerPublicId;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExaminerAnswerScoreStatus status;

    @Column(nullable = false, updatable = false)
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_kind", length = 32)
    private com.pte.scoring.domain.enums.ExaminerDecisionKind decisionKind;

    @Column(name = "reviewed_ai_score")
    private Integer reviewedAiScore;

    @Column(name = "reviewed_ai_version", length = 64)
    private String reviewedAiVersion;

    @Column(name = "expected_review_version", length = 64)
    private String expectedReviewVersion;

    @Column(name = "request_public_id")
    private UUID requestPublicId;

    @Column(name = "request_fingerprint", length = 128)
    private String requestFingerprint;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    public ExaminerAnswerScore(UUID answerPublicId, UUID attemptPublicId, UUID sessionPublicId,
            UUID tenantId, UUID examinerPublicId, int score, Instant submittedAt) {
        this(answerPublicId, attemptPublicId, sessionPublicId, tenantId, examinerPublicId, score,
                submittedAt, null, null, null, null, null, null);
    }

    public ExaminerAnswerScore(UUID answerPublicId, UUID attemptPublicId, UUID sessionPublicId,
            UUID tenantId, UUID examinerPublicId, int score, Instant submittedAt,
            com.pte.scoring.domain.enums.ExaminerDecisionKind decisionKind,
            Integer reviewedAiScore, String reviewedAiVersion,
            String expectedReviewVersion, UUID requestPublicId, String requestFingerprint) {
        this.answerPublicId = Objects.requireNonNull(answerPublicId,
                ScoringDomainConstants.EXAMINER_SCORE_ANSWER_REQUIRED);
        this.attemptPublicId = Objects.requireNonNull(attemptPublicId,
                ScoringDomainConstants.EXAMINER_SCORE_ATTEMPT_REQUIRED);
        this.sessionPublicId = Objects.requireNonNull(sessionPublicId,
                ScoringDomainConstants.EXAMINER_SCORE_SESSION_REQUIRED);
        this.tenantId = Objects.requireNonNull(tenantId, ScoringDomainConstants.EXAMINER_SCORE_TENANT_REQUIRED);
        this.examinerPublicId = Objects.requireNonNull(examinerPublicId,
                ScoringDomainConstants.EXAMINER_SCORE_EXAMINER_REQUIRED);
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException(ScoringDomainConstants.EXAMINER_SCORE_RANGE_INVALID);
        }
        this.score = score;
        this.status = ExaminerAnswerScoreStatus.SUBMITTED;
        this.submittedAt = Objects.requireNonNull(submittedAt,
                ScoringDomainConstants.EXAMINER_SCORE_SUBMISSION_TIME_REQUIRED);
        this.decisionKind = decisionKind;
        this.reviewedAiScore = reviewedAiScore;
        this.reviewedAiVersion = reviewedAiVersion;
        this.expectedReviewVersion = expectedReviewVersion;
        this.requestPublicId = requestPublicId;
        this.requestFingerprint = requestFingerprint;

        if (decisionKind == com.pte.scoring.domain.enums.ExaminerDecisionKind.CONFIRM_AI) {
            if (reviewedAiScore == null || reviewedAiVersion == null || reviewedAiScore != score) {
                throw new IllegalArgumentException(ScoringDomainConstants.EXAMINER_CONFIRM_AI_MISMATCH);
            }
        }
        if (decisionKind != null && decisionKind != com.pte.scoring.domain.enums.ExaminerDecisionKind.LEGACY_IMPORTED) {
            Objects.requireNonNull(requestPublicId, ScoringDomainConstants.EXAMINER_REQUEST_PUBLIC_ID_REQUIRED);
            Objects.requireNonNull(requestFingerprint, "requestFingerprint is required");
            Objects.requireNonNull(expectedReviewVersion, ScoringDomainConstants.EXAMINER_EXPECTED_REVIEW_VERSION_REQUIRED);
        }
    }

    /** Identical retries are safe; a different score or examiner is a conflict, not an update. */
    public void requireIdenticalRetry(UUID retryingExaminerPublicId, int retryingScore) {
        requireIdenticalRetry(retryingExaminerPublicId, retryingScore, null, null);
    }

    public void requireIdenticalRetry(UUID retryingExaminerPublicId, int retryingScore,
            UUID retryingRequestPublicId, String retryingFingerprint) {
        if (!examinerPublicId.equals(retryingExaminerPublicId)) {
            throw new IllegalStateException(ScoringDomainConstants.EXAMINER_SCORE_IMMUTABLE);
        }
        if (requestPublicId != null || (decisionKind != null && decisionKind != com.pte.scoring.domain.enums.ExaminerDecisionKind.LEGACY_IMPORTED)) {
            if (requestPublicId == null || retryingRequestPublicId == null
                    || !requestPublicId.equals(retryingRequestPublicId)) {
                throw new IllegalStateException(ScoringDomainConstants.EXAMINER_SCORE_IMMUTABLE);
            }
            if (!Objects.equals(requestFingerprint, retryingFingerprint)) {
                throw new IllegalStateException(ScoringDomainConstants.EXAMINER_RECEIPT_CONFLICT);
            }
            return;
        }
        if (score != retryingScore) {
            throw new IllegalStateException(ScoringDomainConstants.EXAMINER_SCORE_IMMUTABLE);
        }
    }

    public boolean isPublishable() {
        return status == ExaminerAnswerScoreStatus.SUBMITTED && score >= 0 && score <= 100;
    }

    @PreUpdate
    private void preventUpdate() {
        throw new IllegalStateException(ScoringDomainConstants.EXAMINER_SCORES_IMMUTABLE);
    }

    @PreRemove
    private void preventDelete() {
        throw new IllegalStateException(ScoringDomainConstants.EXAMINER_SCORES_IMMUTABLE);
    }
}
