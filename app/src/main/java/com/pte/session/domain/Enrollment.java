package com.pte.session.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A student manually enrolled into a session, referenced by identity's
 * {@code publicId} (no user data duplicated here). Named {@code
 * session.Enrollment} — distinct from {@code enrollment.ClassMembership}
 * (a student's membership in a class); this is "who may sit this exam
 * session," not "who belongs to this class." The DB unique constraint is the
 * race-condition-safe guard against double enrollment, not an app-level
 * check-then-act.
 */
@Entity
@Table(name = "enrollments", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"session_id", "student_public_id"})
}, indexes = {
        @Index(name = "idx_enrollments_session", columnList = "session_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Enrollment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamSession session;

    @Column(name = "student_public_id", nullable = false)
    private UUID studentPublicId;

    @Column(nullable = false)
    private UUID tenantId;

    /**
     * Denormalized license identifier for audit and license-control queries.
     * Enrollment correctness and per-session capacity still use {@code session_id}.
     */
    @Column(nullable = false, length = 64)
    private String licenseKey;
}
