package com.pte.reporting.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * reporting's own bounded fact about one attempt: whether its report is
 * visible yet. Created lazily (host viewing before publish, or the publish
 * command itself) from attempt's canonical {@code AttemptSummaryView} —
 * {@code published} is the ONLY field this module decides for itself, the
 * visibility gate; everything else here is a stable copy of what attempt
 * already owns.
 */
@Entity
@Table(name = "attempt_reports", indexes = {
        @Index(name = "idx_attempt_reports_session", columnList = "session_public_id")
})
@Getter
@Setter
@NoArgsConstructor
public class AttemptReport extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID attemptPublicId;

    @Column(nullable = false)
    private UUID sessionPublicId;

    @Column(nullable = false)
    private UUID studentPublicId;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private boolean published = false;

    @Column
    private Instant publishedAt;

    public void publish() {
        this.published = true;
        this.publishedAt = Instant.now();
    }
}
