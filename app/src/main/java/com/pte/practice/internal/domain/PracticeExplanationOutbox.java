package com.pte.practice.internal.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Commit-side message intent. RabbitMQ is a delivery mechanism, not the source of truth. */
@Entity
@Table(name = "practice_explanation_outbox", indexes = {
        @Index(name = "idx_practice_explanation_outbox_due", columnList = "published_at,next_attempt_at")
})
@Getter
@Setter
@NoArgsConstructor
public class PracticeExplanationOutbox extends BaseEntity {

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_failure_code", length = 64)
    private String lastFailureCode;
}
