package com.pte.session.domain;

import com.pte.session.domain.enums.SessionStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
 * A scheduled exam window, referencing a published assessment snapshot by
 * {@code publicId} (never a cross-module JOIN — module boundary is code, not
 * FK). A student pins every item of that snapshot at attempt-create time —
 * there is no host-chosen subset (the random-exam-generation step already
 * applies the skill selection at exam-creation time, not delivery time).
 * Subscription and license references are scalar denormalized values;
 * billing remains a separate module, consulted only as a creation-time gate
 * (window/capacity/overlap).
 */
@Entity
@Table(name = "exam_sessions", indexes = {
        @Index(name = "idx_sessions_tenant", columnList = "tenant_id"),
        @Index(name = "idx_sessions_subscription", columnList = "subscription_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ExamSession extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID subscriptionId;

    @Column(nullable = false, length = 64)
    private String licenseKey;

    @Column(nullable = false)
    private UUID snapshotPublicId;

    @Column(nullable = false)
    private Instant opensAt;

    @Column(nullable = false)
    private Instant closesAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.SCHEDULED;

    /** Snapshot of the requested per-session capacity, bounded by the subscription cap. */
    @Column(nullable = false)
    private Integer capacity;

    @Embedded
    private ExamPolicy policy = ExamPolicy.mockTestDefault();

    public void open() {
        if (status != SessionStatus.CANCELLED) {
            this.status = SessionStatus.OPEN;
        }
    }

    public void close() {
        if (status != SessionStatus.CANCELLED) {
            this.status = SessionStatus.CLOSED;
        }
    }

    public void cancel() {
        if (status == SessionStatus.SCHEDULED) {
            this.status = SessionStatus.CANCELLED;
        }
    }
}
