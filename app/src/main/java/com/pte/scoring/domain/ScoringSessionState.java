package com.pte.scoring.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Session-level scoring publication barrier, owned by scoring and addressed by cross-module public IDs. */
@Entity
@Table(name = "scoring_session_states",
        uniqueConstraints = @UniqueConstraint(name = "uq_scoring_session_state_tenant_session",
                columnNames = {"tenant_id", "session_public_id"}))
@Getter
@NoArgsConstructor
public class ScoringSessionState extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID sessionPublicId;

    @Column
    private UUID publicationPublicId;

    @Column
    private Instant publicationLockedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    public ScoringSessionState(UUID tenantId, UUID sessionPublicId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId is required");
        this.sessionPublicId = Objects.requireNonNull(sessionPublicId, "sessionPublicId is required");
    }

    public void lockForPublication(UUID publicationPublicId, Instant lockedAt) {
        Objects.requireNonNull(publicationPublicId, "publicationPublicId is required");
        Objects.requireNonNull(lockedAt, "lockedAt is required");
        if (this.publicationPublicId != null && !this.publicationPublicId.equals(publicationPublicId)) {
            throw new IllegalStateException("Session scoring is already locked by another publication");
        }
        if (this.publicationPublicId != null) {
            return;
        }
        this.publicationPublicId = publicationPublicId;
        this.publicationLockedAt = lockedAt;
    }

    @PreRemove
    private void preventDelete() {
        throw new IllegalStateException("Scoring publication locks cannot be removed");
    }
}
