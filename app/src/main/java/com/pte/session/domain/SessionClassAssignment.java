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
 * Records that a Class (from {@code enrollment}, referenced by {@code
 * publicId} — never a cross-module FK) has been assigned to this session.
 * The actual roster effect is {@code session.Enrollment} rows, created via
 * {@code EnrollmentService.bulkEnroll}; this table only remembers WHICH
 * Classes were assigned, so a re-assign or unassign knows what to do.
 */
@Entity
@Table(name = "session_class_assignments", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"session_id", "class_public_id"})
}, indexes = {
        @Index(name = "idx_session_class_assignments_session", columnList = "session_id")
})
@Getter
@Setter
@NoArgsConstructor
public class SessionClassAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamSession session;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(name = "class_public_id", nullable = false)
    private UUID classPublicId;
}
