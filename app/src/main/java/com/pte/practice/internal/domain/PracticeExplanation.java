package com.pte.practice.internal.domain;

import com.pte.practice.internal.domain.enums.PracticeExplanationStatus;
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
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Immutable explanation snapshot for one submitted practice item and language. */
@Entity
@Table(name = "practice_explanations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_practice_explanation_slot",
                columnNames = {"practice_session_item_id", "language", "generation_sequence"})
}, indexes = {
        @Index(name = "idx_practice_explanation_item", columnList = "practice_session_item_id,language")
})
@Getter
@Setter
@NoArgsConstructor
public class PracticeExplanation extends BaseEntity {

    @Column(name = "practice_session_item_id", nullable = false)
    private Long practiceSessionItemId;

    @Column(name = "student_public_id", nullable = false)
    private UUID studentPublicId;

    @Column(nullable = false, length = 5)
    private String language;

    @Column(name = "generation_sequence", nullable = false)
    private int generationSequence;

    @Column(name = "submission_hash", nullable = false, length = 64)
    private String submissionHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PracticeExplanationStatus status;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "strengths_json", columnDefinition = "text")
    private String strengthsJson;

    @Column(name = "improvements_json", columnDefinition = "text")
    private String improvementsJson;

    @Column(name = "rubric_feedback_json", columnDefinition = "text")
    private String rubricFeedbackJson;

    @Column(name = "rubric_version", nullable = false, length = 64)
    private String rubricVersion;

    @Column(name = "requested_model", length = 128)
    private String requestedModel;

    @Column(name = "actual_model_version", length = 128)
    private String actualModelVersion;

    @Column(name = "provider_version", length = 128)
    private String providerVersion;

    @Column(name = "generated_at")
    private Instant generatedAt;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "can_retry", nullable = false)
    private boolean canRetry;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;
}
