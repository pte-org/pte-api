package com.pte.practice.internal.domain;

import com.pte.practice.internal.domain.enums.PracticeExplanationJobStatus;
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

/** Durable generation identity. The queue carries only this public id. */
@Entity
@Table(name = "practice_explanation_jobs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_practice_explanation_job_explanation", columnNames = "explanation_id"),
        @UniqueConstraint(name = "uk_practice_explanation_job_idempotency",
                columnNames = {"student_public_id", "idempotency_key"})
}, indexes = {
        @Index(name = "idx_practice_explanation_job_due", columnList = "status,next_attempt_at"),
        @Index(name = "idx_practice_explanation_job_lease", columnList = "status,lease_until")
})
@Getter
@Setter
@NoArgsConstructor
public class PracticeExplanationJob extends BaseEntity {

    @Column(name = "explanation_id", nullable = false)
    private Long explanationId;

    @Column(name = "student_public_id", nullable = false)
    private UUID studentPublicId;

    @Column(nullable = false, length = 5)
    private String language;

    @Column(name = "generation_sequence", nullable = false)
    private int generationSequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PracticeExplanationJobStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "last_failure_code", length = 64)
    private String lastFailureCode;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "run_token", nullable = false)
    private UUID runToken;
}
