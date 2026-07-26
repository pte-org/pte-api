package com.pte.scheduling.domain;

import com.pte.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A student manually enrolled into a session, referenced by iam's
 * {@code publicId} (no user data duplicated here — §3). The DB unique
 * constraint is the race-condition-safe guard against double enrollment
 * (CODING_STANDARDS_API.md concurrency rule), not an app-level check-then-act.
 */
@Entity
@Table(name = "enrollments", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"session_id", "student_public_id"})
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
}
