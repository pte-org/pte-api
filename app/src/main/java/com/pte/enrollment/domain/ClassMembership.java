package com.pte.enrollment.domain;

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
 * A pure join-table fact â€” a student is or isn't in a Class â€” same category as
 * {@code scheduling.Enrollment}/{@code ProctorAssignment}. {@code studentPublicId}
 * is bare (iam's {@code publicId}, never a cross-service FK), and is unique on
 * its own (not per-class): a student can only ever have one membership row,
 * which is exactly what makes "transfer" a simple FK update rather than a
 * delete+recreate. {@code tenantId} is denormalized here (rather than only
 * derivable via {@code studentClass -> program -> organization -> tenant})
 * because the tenant-wide roster endpoint queries this table far more often
 * than any single-class lookup does.
 */
@Entity
@Table(name = "class_memberships", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"student_public_id"})
}, indexes = {
        @Index(name = "idx_class_memberships_class", columnList = "student_class_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ClassMembership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_class_id", nullable = false)
    private StudentClass studentClass;

    @Column(name = "student_public_id", nullable = false)
    private UUID studentPublicId;

    @Column(nullable = false)
    private UUID tenantId;
}
