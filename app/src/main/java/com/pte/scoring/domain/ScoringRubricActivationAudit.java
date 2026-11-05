package com.pte.scoring.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per rubric-version activation: which version, the ACTIVE version it replaced, who activated it, when, and
 * a summary of the parameter diff confirmed before activation. Append-only: the V32 trigger rejects UPDATE/DELETE.
 */
@Entity
@Table(name = "scoring_rubric_activation_audits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoringRubricActivationAudit extends BaseEntity {

    @Column(name = "version_public_id", nullable = false, updatable = false)
    private UUID versionPublicId;

    @Column(name = "previous_active_public_id", updatable = false)
    private UUID previousActivePublicId;

    @Column(name = "actor_user_public_id", nullable = false, updatable = false)
    private UUID actorUserPublicId;

    @Column(name = "activated_at", nullable = false, updatable = false)
    private Instant activatedAt;

    @Column(name = "diff_summary", columnDefinition = "text", updatable = false)
    private String diffSummary;

    public ScoringRubricActivationAudit(UUID versionPublicId, UUID previousActivePublicId, UUID actorUserPublicId,
                                        Instant activatedAt, String diffSummary) {
        this.versionPublicId = versionPublicId;
        this.previousActivePublicId = previousActivePublicId;
        this.actorUserPublicId = actorUserPublicId;
        this.activatedAt = activatedAt;
        this.diffSummary = diffSummary;
    }
}
