package com.pte.reporting.domain;

import com.pte.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * reporting's projection of one attempt (built from exam-delivery's
 * {@code AttemptSubmitted}). Owns no source-of-truth — {@code published} is
 * the ONLY field this service decides for itself (the visibility gate),
 * everything else is derived from consumed events.
 */
@Entity
@Table(name = "attempt_reports")
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
