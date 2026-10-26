package com.pte.session.domain;

import com.pte.session.domain.enums.AttendanceStatus;
import com.pte.session.domain.enums.AudienceDecisionReason;
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.session.domain.enums.IdentityVerificationStatus;
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

/**
 * One student's place in one exam session: the audience decision made while the session is a draft
 * (eligible, excluded and why), the enrollment once the session is published ({@link AudienceMemberStatus#ENROLLED}),
 * and the attendance and identity-verification state a host or proctor records afterwards. A registration is
 * "enrolled" only in status {@code ENROLLED}; every roster, capacity and conflict query filters on it.
 * <p>
 * The unique {@code (session, student)} constraint is the race-safe guard against registering a student twice.
 * {@code publicId} is the enrollment id the roster and attendance endpoints expose. Idempotency receipts and
 * the attendance history of this row live in {@code IdempotencyKey} and {@code AuditLog}, not here.
 */
@Entity
@Table(name = "exam_registrations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"session_id", "student_public_id"})
}, indexes = {
        @Index(name = "idx_exam_registrations_session", columnList = "session_id"),
        @Index(name = "idx_exam_registrations_student", columnList = "tenant_id, student_public_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ExamRegistration extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamSession session;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "student_public_id", nullable = false)
    private UUID studentPublicId;

    /** Denormalized for audit and license-control queries; capacity is counted per session. */
    @Column(nullable = false, length = 64)
    private String licenseKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AudienceMemberStatus status = AudienceMemberStatus.ELIGIBLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 32)
    private AudienceDecisionReason reason;

    @Column(name = "source_summary", length = 1000)
    private String sourceSummary;

    @Column(name = "prior_session_public_id")
    private UUID priorSessionPublicId;

    @Column(name = "prior_session_name", length = 255)
    private String priorSessionName;

    @Column(name = "prior_status", length = 16)
    private String priorStatus;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false, length = 32)
    private AttendanceStatus attendanceStatus = AttendanceStatus.UNMARKED;

    /** Optimistic version of the attendance and identity state; every recorded change increments it. */
    @Column(name = "attendance_version", nullable = false)
    private long attendanceVersion = 0L;

    @Column(name = "last_actor_public_id")
    private UUID lastActorPublicId;

    @Column(name = "last_actor_role", length = 32)
    private String lastActorRole;

    @Column(name = "last_action_at")
    private Instant lastActionAt;

    @Column(name = "last_reason_or_note", length = 1000)
    private String lastReasonOrNote;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_status", nullable = false, length = 32)
    private IdentityVerificationStatus identityStatus = IdentityVerificationStatus.UNVERIFIED;

    @Column(name = "identity_note", length = 1000)
    private String identityNote;

    public boolean isEnrolled() {
        return status == AudienceMemberStatus.ENROLLED;
    }
}
