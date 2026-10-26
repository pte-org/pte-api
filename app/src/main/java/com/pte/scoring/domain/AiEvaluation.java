package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The automatic evaluation of one answer: scored by rules (objective task types) or by an AI provider. It carries
 * the work state of that scoring — status, the dispatch token that makes a late worker result harmless, the
 * provider that produced the result — and what lies behind an AI score (per-criterion scores, feedback,
 * transcript) or why the latest attempt failed. How the answer is scored ({@code scoringMethod}, {@code section})
 * is read once from the exam item when the row is created and never looked up again.
 */
@Entity
@DiscriminatorValue(AiEvaluation.DISCRIMINATOR)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiEvaluation extends AnswerEvaluation {

    static final String DISCRIMINATOR = "AI";

    @Column(name = "task_type")
    private String taskType;

    /** The exam version item this answer responds to. */
    @Column(name = "item_public_id")
    private UUID itemPublicId;

    @Column(name = "exam_definition_public_id")
    private UUID examDefinitionPublicId;

    @Column(name = "section")
    private String section;

    /** Null when the exam has no scoring rule for this task type; such answers stay PENDING. */
    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_method", length = 16)
    private ScoringMethod scoringMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16)
    @Setter
    private ScoringAnswerStatus status = ScoringAnswerStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "ai_provider_category", length = 16)
    private AiProviderCategory aiProviderCategory;

    @Column(name = "ai_provider", length = 64)
    private String aiProvider;

    @Column(name = "ai_model", length = 160)
    private String aiModel;

    @Column(name = "ai_provider_version", length = 100)
    private String aiProviderVersion;

    /** Identifies the latest AI dispatch; a worker result counts only while the row still awaits this token. */
    @Column(name = "dispatch_token")
    private long dispatchToken;

    @Column(name = "ai_dispatched_at")
    private Instant aiDispatchedAt;

    /** A host's own independent score, parallel to the automatic one and never gating it. */
    @Column(name = "teacher_score")
    @Setter
    private Integer teacherScore;

    @Column(name = "teacher_scored_at")
    @Setter
    private Instant teacherScoredAt;

    @Column(name = "criteria_scores_json", columnDefinition = "text")
    private String criteriaScoresJson;

    @Column(name = "feedback", columnDefinition = "text")
    private String feedback;

    @Column(name = "transcript", columnDefinition = "text")
    private String transcript;

    @Column(name = "detail_json", columnDefinition = "text")
    private String detailJson;

    @Column(name = "fallback")
    private boolean fallback;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 40)
    private AiFailureReason failureReason;

    public AiEvaluation(UUID tenantId, UUID sessionPublicId, UUID attemptPublicId, UUID answerPublicId,
            UUID itemPublicId, UUID examDefinitionPublicId, String taskType, String section,
            ScoringMethod scoringMethod) {
        super(DISCRIMINATOR, tenantId, sessionPublicId, attemptPublicId, answerPublicId);
        this.itemPublicId = itemPublicId;
        this.examDefinitionPublicId = examDefinitionPublicId;
        this.taskType = taskType;
        this.section = section;
        this.scoringMethod = scoringMethod;
    }

    public Integer getRawScore() {
        return getScore();
    }

    /** @return the new dispatch token the queued job must carry */
    public long markAiDispatched(Instant dispatchedAt) {
        this.status = ScoringAnswerStatus.AI_SCORING;
        this.aiDispatchedAt = dispatchedAt;
        return ++dispatchToken;
    }

    /**
     * Whether a host's next scoring request would send this answer to the AI again: it failed, or its job was lost
     * (never dispatched, or dispatched before {@code staleBefore}). Same rule as {@code findScoringWork}, minus PENDING.
     */
    public boolean isRescorable(Instant staleBefore) {
        return status == ScoringAnswerStatus.SCORING_FAILED
                || (status == ScoringAnswerStatus.AI_SCORING
                && (aiDispatchedAt == null || aiDispatchedAt.isBefore(staleBefore)));
    }

    public boolean isAwaitingAiResult(long token) {
        return status == ScoringAnswerStatus.AI_SCORING && dispatchToken == token;
    }

    /** A job's result is wanted only by the answer it was queued for, while that dispatch is the latest. */
    public boolean isAwaitingAiResult(UUID tenantId, UUID sessionPublicId, UUID attemptPublicId, long token) {
        return Objects.equals(getTenantId(), tenantId)
                && Objects.equals(getSessionPublicId(), sessionPublicId)
                && Objects.equals(getAttemptPublicId(), attemptPublicId)
                && isAwaitingAiResult(token);
    }

    public void markScored(int rawScore, Instant now) {
        if (rawScore < 0 || rawScore > 100) {
            throw new IllegalArgumentException(ScoringDomainConstants.RAW_SCORE_RANGE_INVALID);
        }
        this.status = ScoringAnswerStatus.SCORED;
        recordScore(rawScore, now);
    }

    public void markAiScored(int rawScore, AiProviderCategory providerCategory, String provider,
            String model, String providerVersion, Instant now) {
        if (providerCategory == null || provider == null || provider.isBlank()) {
            throw new IllegalArgumentException(ScoringDomainConstants.AI_SCORE_PROVENANCE_REQUIRED);
        }
        markScored(rawScore, now);
        this.aiProviderCategory = providerCategory;
        this.aiProvider = provider;
        this.aiModel = model;
        this.aiProviderVersion = providerVersion;
    }

    public boolean hasPublishableAiScore() {
        return status == ScoringAnswerStatus.SCORED && getScore() != null && getScore() >= 0 && getScore() <= 100
                && aiProviderCategory == AiProviderCategory.REAL && aiProvider != null && !aiProvider.isBlank();
    }

    /** Whether an AI provider has produced a result or a failure for this row; an objective answer never has. */
    public boolean hasAiOutcome() {
        return aiProviderCategory != null || failureReason != null;
    }

    /** Keeps the last successful result's detail, if any; the status says the latest attempt failed. */
    public void recordFailure(AiFailureReason reason) {
        this.failureReason = reason;
    }

    public void recordDetail(String criteriaScoresJson, String feedback, String transcript, String detailJson,
            boolean fallback) {
        this.criteriaScoresJson = criteriaScoresJson;
        this.feedback = feedback;
        this.transcript = transcript;
        this.detailJson = detailJson;
        this.fallback = fallback;
        this.failureReason = null;
    }
}
