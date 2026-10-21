package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * What lies behind an AI score — per-criterion scores, feedback, transcript —
 * or why the latest AI attempt failed. Kept apart from {@link ScoringAnswer}
 * so the work row stays small; one row per answer, overwritten on rescore.
 */
@Entity
@Table(name = "scoring_answer_ai_details")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoringAnswerAiDetail extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID answerPublicId;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(columnDefinition = "text")
    private String criteriaScoresJson;

    @Column(columnDefinition = "text")
    private String feedback;

    @Column(columnDefinition = "text")
    private String transcript;

    @Column(columnDefinition = "text")
    private String detailJson;

    @Column(nullable = false)
    private boolean fallback;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private AiFailureReason failureReason;

    public ScoringAnswerAiDetail(UUID answerPublicId, UUID tenantId) {
        this.answerPublicId = answerPublicId;
        this.tenantId = tenantId;
    }

    public void recordResult(String criteriaScoresJson, String feedback, String transcript, String detailJson,
            boolean fallback) {
        this.criteriaScoresJson = criteriaScoresJson;
        this.feedback = feedback;
        this.transcript = transcript;
        this.detailJson = detailJson;
        this.fallback = fallback;
        this.failureReason = null;
    }

    /** Keeps the last successful result's detail, if any; the status on the answer row says it failed. */
    public void recordFailure(AiFailureReason reason) {
        this.failureReason = reason;
    }
}
