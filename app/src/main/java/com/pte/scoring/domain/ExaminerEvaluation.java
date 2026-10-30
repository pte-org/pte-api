package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus;
import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PreRemove;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An examiner evaluation of one answer: a draft while the work is open, an immutable 0-100 score once
 * submitted. It is distinct from the AI score and from the host teacher score. A submitted row never changes
 * again — a retry must be identical, a different score or examiner is a conflict, not an update.
 */
@Entity
@DiscriminatorValue(ExaminerEvaluation.DISCRIMINATOR)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExaminerEvaluation extends AnswerEvaluation {

    static final String DISCRIMINATOR = "EXAMINER";

    @Column(name = "examiner_public_id")
    private UUID examinerPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "examiner_status", length = 16)
    private ExaminerAnswerScoreStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_kind", length = 32)
    private ExaminerDecisionKind decisionKind;

    @Column(name = "reviewed_ai_score")
    private Integer reviewedAiScore;

    @Column(name = "reviewed_ai_version", length = 128)
    private String reviewedAiVersion;

    @Column(name = "expected_review_version", length = 128)
    private String expectedReviewVersion;

    @Column(name = "request_public_id")
    private UUID requestPublicId;

    @Column(name = "request_fingerprint", length = 128)
    private String requestFingerprint;

    /** Draft state: the work it belongs to, the note, and how many times it was saved. */
    @Column(name = "assignment_id")
    private Long assignmentId;

    @Column(name = "comment", length = 2000)
    private String comment;

    @Column(name = "draft_version")
    private long draftVersion;

    @Column(name = "saved_at")
    private Instant savedAt;

    @Transient
    private boolean loadedSubmitted;

    private ExaminerEvaluation(UUID tenantId, UUID sessionPublicId, UUID attemptPublicId, UUID answerPublicId,
            UUID examinerPublicId) {
        super(DISCRIMINATOR, Objects.requireNonNull(tenantId, ScoringDomainConstants.EXAMINER_SCORE_TENANT_REQUIRED),
                Objects.requireNonNull(sessionPublicId, ScoringDomainConstants.EXAMINER_SCORE_SESSION_REQUIRED),
                Objects.requireNonNull(attemptPublicId, ScoringDomainConstants.EXAMINER_SCORE_ATTEMPT_REQUIRED),
                Objects.requireNonNull(answerPublicId, ScoringDomainConstants.EXAMINER_SCORE_ANSWER_REQUIRED));
        this.examinerPublicId = Objects.requireNonNull(examinerPublicId,
                ScoringDomainConstants.EXAMINER_SCORE_EXAMINER_REQUIRED);
    }

    /** A new draft; its version starts at 1. */
    public static ExaminerEvaluation draft(UUID tenantId, UUID sessionPublicId, UUID attemptPublicId,
            UUID answerPublicId, UUID examinerPublicId, Long assignmentId, Integer score, String comment,
            ExaminerDecisionKind decisionKind, String expectedReviewVersion, String reviewedAiVersion,
            Instant savedAt) {
        ExaminerEvaluation draft = new ExaminerEvaluation(tenantId, sessionPublicId, attemptPublicId, answerPublicId,
                examinerPublicId);
        draft.status = ExaminerAnswerScoreStatus.DRAFT;
        draft.assignmentId = assignmentId;
        draft.draftVersion = 1L;
        draft.applyDraft(score, comment, decisionKind, expectedReviewVersion, reviewedAiVersion, savedAt);
        return draft;
    }

    /** A score submitted directly, without a draft. */
    public static ExaminerEvaluation submittedDirectly(UUID tenantId, UUID sessionPublicId, UUID attemptPublicId,
            UUID answerPublicId, UUID examinerPublicId, int score, Instant submittedAt,
            ExaminerDecisionKind decisionKind, Integer reviewedAiScore, String reviewedAiVersion,
            String expectedReviewVersion, UUID requestPublicId, String requestFingerprint) {
        ExaminerEvaluation evaluation = new ExaminerEvaluation(tenantId, sessionPublicId, attemptPublicId,
                answerPublicId, examinerPublicId);
        evaluation.submit(score, submittedAt, decisionKind, reviewedAiScore, reviewedAiVersion,
                expectedReviewVersion, requestPublicId, requestFingerprint);
        return evaluation;
    }

    public void updateDraft(Integer score, String comment, ExaminerDecisionKind decisionKind,
            String expectedReviewVersion, String reviewedAiVersion, Instant savedAt) {
        requireDraft();
        applyDraft(score, comment, decisionKind, expectedReviewVersion, reviewedAiVersion, savedAt);
        this.draftVersion++;
    }

    /** Turns this row into the final score. Validation matches a directly submitted score. */
    public void submit(int score, Instant submittedAt, ExaminerDecisionKind decisionKind, Integer reviewedAiScore,
            String reviewedAiVersion, String expectedReviewVersion, UUID requestPublicId,
            String requestFingerprint) {
        requireDraft();
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException(ScoringDomainConstants.EXAMINER_SCORE_RANGE_INVALID);
        }
        Objects.requireNonNull(submittedAt, ScoringDomainConstants.EXAMINER_SCORE_SUBMISSION_TIME_REQUIRED);
        if (decisionKind == ExaminerDecisionKind.CONFIRM_AI
                && (reviewedAiScore == null || reviewedAiVersion == null || reviewedAiScore != score)) {
            throw new IllegalArgumentException(ScoringDomainConstants.EXAMINER_CONFIRM_AI_MISMATCH);
        }
        if (decisionKind != null && decisionKind != ExaminerDecisionKind.LEGACY_IMPORTED) {
            Objects.requireNonNull(requestPublicId, ScoringDomainConstants.EXAMINER_REQUEST_PUBLIC_ID_REQUIRED);
            Objects.requireNonNull(requestFingerprint, ScoringDomainConstants.EXAMINER_FINGERPRINT_REQUIRED);
            Objects.requireNonNull(expectedReviewVersion,
                    ScoringDomainConstants.EXAMINER_EXPECTED_REVIEW_VERSION_REQUIRED);
        }
        this.status = ExaminerAnswerScoreStatus.SUBMITTED;
        recordScore(score, submittedAt);
        this.submittedAt = submittedAt;
        this.decisionKind = decisionKind;
        this.reviewedAiScore = reviewedAiScore;
        this.reviewedAiVersion = reviewedAiVersion;
        this.expectedReviewVersion = expectedReviewVersion;
        this.requestPublicId = requestPublicId;
        this.requestFingerprint = requestFingerprint;
    }

    public boolean isSubmitted() {
        return status == ExaminerAnswerScoreStatus.SUBMITTED;
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
        if (requestPublicId != null
                || (decisionKind != null && decisionKind != ExaminerDecisionKind.LEGACY_IMPORTED)) {
            if (requestPublicId == null || retryingRequestPublicId == null
                    || !requestPublicId.equals(retryingRequestPublicId)) {
                throw new IllegalStateException(ScoringDomainConstants.EXAMINER_SCORE_IMMUTABLE);
            }
            if (!Objects.equals(requestFingerprint, retryingFingerprint)) {
                throw new IllegalStateException(ScoringDomainConstants.EXAMINER_RECEIPT_CONFLICT);
            }
            return;
        }
        if (!Objects.equals(getScore(), retryingScore)) {
            throw new IllegalStateException(ScoringDomainConstants.EXAMINER_SCORE_IMMUTABLE);
        }
    }

    public boolean isPublishable() {
        return isSubmitted() && getScore() != null && getScore() >= 0 && getScore() <= 100;
    }

    private void applyDraft(Integer score, String comment, ExaminerDecisionKind decisionKind,
            String expectedReviewVersion, String reviewedAiVersion, Instant savedAt) {
        recordScore(score, null);
        this.comment = comment;
        this.decisionKind = decisionKind;
        this.expectedReviewVersion = expectedReviewVersion;
        this.reviewedAiVersion = reviewedAiVersion;
        this.savedAt = savedAt;
    }

    private void requireDraft() {
        if (status == ExaminerAnswerScoreStatus.SUBMITTED) {
            throw new IllegalStateException(ScoringDomainConstants.EXAMINER_SCORE_IMMUTABLE);
        }
    }

    @PostLoad
    private void rememberSubmitted() {
        this.loadedSubmitted = status == ExaminerAnswerScoreStatus.SUBMITTED;
    }

    @PreUpdate
    private void preventUpdate() {
        if (loadedSubmitted) {
            throw new IllegalStateException(ScoringDomainConstants.EXAMINER_SCORES_IMMUTABLE);
        }
    }

    @PreRemove
    private void preventDelete() {
        if (status == ExaminerAnswerScoreStatus.SUBMITTED) {
            throw new IllegalStateException(ScoringDomainConstants.EXAMINER_SCORES_IMMUTABLE);
        }
    }
}
