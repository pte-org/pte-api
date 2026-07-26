package com.pte.proctor.domain;

import com.pte.common.domain.BaseEntity;
import com.pte.proctor.domain.enums.ViolationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One append-only, tamper-evident audit row. {@code hash} =
 * {@code SHA256(proctorSession.publicId | sequenceNo | attemptPublicId |
 * violationType | detail | detectedAt | prevHash)} — see
 * {@code HashChainService}. Rows are never updated or deleted; integrity is
 * verified by replaying the chain in {@code sequenceNo} order.
 */
@Entity
@Table(name = "violation_events")
@Getter
@Setter
@NoArgsConstructor
public class ViolationEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proctor_session_id", nullable = false)
    private ProctorSession proctorSession;

    /** Denormalized from the owning {@link ProctorSession} at creation — lets audit queries filter by exam session without a join. */
    @Column(name = "session_public_id", nullable = false)
    private UUID sessionPublicId;

    @Column(name = "attempt_public_id", nullable = false)
    private UUID attemptPublicId;

    @Column(nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "violation_type", nullable = false)
    private ViolationType violationType;

    @Column
    private String detail;

    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    @Column(name = "prev_hash")
    private String prevHash;

    @Column(nullable = false)
    private String hash;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();
}
