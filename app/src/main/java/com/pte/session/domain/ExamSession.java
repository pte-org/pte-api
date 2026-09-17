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
 * there is no host-chosen subset (Plan B removed {@code SessionComposition};
 * the random-exam-generation step already applies the skill selection at
 * exam-creation time, not delivery time).
 */
@Entity
@Table(name = "exam_sessions", indexes = {
        @Index(name = "idx_sessions_tenant", columnList = "tenant_id")
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
    private UUID snapshotPublicId;

    @Column(nullable = false)
    private Instant opensAt;

    @Column(nullable = false)
    private Instant closesAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status = SessionStatus.SCHEDULED;

    /** Null = unlimited. Enforced in {@code EnrollmentService.bulkEnroll}. */
    private Integer capacity;

    @Embedded
    private ExamPolicy policy = ExamPolicy.mockTestDefault();

    public void open() {
        this.status = SessionStatus.OPEN;
    }

    public void close() {
        this.status = SessionStatus.CLOSED;
    }
}
