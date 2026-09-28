package com.pte.attempt.domain;

import com.pte.attempt.domain.enums.LockdownViolationSeverity;
import com.pte.attempt.domain.enums.LockdownViolationType;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Server-received lockdown evidence emitted by a student attempt. */
@Entity
@Table(name = "attempt_security_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_attempt_security_events_attempt_client",
                columnNames = {"attempt_id", "client_event_id"})
}, indexes = {
        @Index(name = "idx_attempt_security_events_tenant_session_detected",
                columnList = "tenant_id, session_public_id, detected_at"),
        @Index(name = "idx_attempt_security_events_attempt_detected",
                columnList = "attempt_public_id, detected_at"),
        @Index(name = "idx_attempt_security_events_attempt_client",
                columnList = "attempt_id, client_event_id")
})
@Getter
@Setter
@NoArgsConstructor
public class AttemptSecurityEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, updatable = false)
    private ExamAttempt attempt;

    @Column(name = "attempt_public_id", nullable = false, updatable = false)
    private UUID attemptPublicId;

    @Column(name = "session_public_id", nullable = false, updatable = false)
    private UUID sessionPublicId;

    @Column(name = "student_public_id", nullable = false, updatable = false)
    private UUID studentPublicId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "client_event_id", nullable = false, length = 128, updatable = false)
    private String clientEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "violation_type", nullable = false, length = 64, updatable = false)
    private LockdownViolationType violationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private LockdownViolationSeverity severity;

    @Column(columnDefinition = "TEXT", length = 2048, updatable = false)
    private String detail;

    @Column(name = "client_occurred_at", updatable = false)
    private Instant clientOccurredAt;

    @Column(name = "detected_at", nullable = false, updatable = false)
    private Instant detectedAt;
}
