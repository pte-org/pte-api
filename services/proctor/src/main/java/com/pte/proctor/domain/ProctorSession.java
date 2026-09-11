package com.pte.proctor.domain;

import com.pte.common.domain.BaseEntity;
import com.pte.proctor.domain.enums.ProctorSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A proctor's live monitoring window over one {@code ExamSession} (scheduling's
 * aggregate, referenced by {@code publicId} only — no shared DB, ADR-001).
 * {@code lastSequenceNo}/{@code lastHash} are the running head of this
 * session's tamper-evident {@link ViolationEvent} hash chain; both advance in
 * the same transaction as each new violation row, so there is no race window
 * between "read the current head" and "write the next link."
 */
@Entity
@Table(name = "proctor_sessions", indexes = {
        @Index(name = "idx_proctor_sessions_session_proctor", columnList = "session_public_id, proctor_public_id"),
        @Index(name = "idx_proctor_sessions_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ProctorSession extends BaseEntity {

    @Column(name = "session_public_id", nullable = false)
    private UUID sessionPublicId;

    @Column(name = "proctor_public_id", nullable = false)
    private UUID proctorPublicId;

    @Column(nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProctorSessionStatus status = ProctorSessionStatus.ACTIVE;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt = Instant.now();

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "last_sequence_no", nullable = false)
    private int lastSequenceNo = 0;

    @Column(name = "last_hash")
    private String lastHash;

    public boolean isActive() {
        return status == ProctorSessionStatus.ACTIVE;
    }

    public void end() {
        this.status = ProctorSessionStatus.ENDED;
        this.closedAt = Instant.now();
    }

    public void advanceChain(String newHash) {
        this.lastSequenceNo = this.lastSequenceNo + 1;
        this.lastHash = newHash;
    }
}
