package com.pte.admin.domain;

import com.pte.common.domain.BaseEntity;
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
 * A Lecturer assigned to supervise a {@link StudentClass} — "just like a
 * Proctor," mirrors {@code scheduling.ProctorAssignment}'s shape exactly:
 * target entity + bare {@code assigneePublicId} (iam's {@code publicId},
 * never a cross-service FK) + denormalized {@code tenantId}. No role
 * sub-enum, unlike {@code ProctorAssignment} — the feature spec doesn't ask
 * for a Lecturer sub-classification.
 */
@Entity
@Table(name = "lecturer_assignments", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"student_class_id", "assignee_public_id"})
}, indexes = {
        @Index(name = "idx_lecturer_assignments_class", columnList = "student_class_id")
})
@Getter
@Setter
@NoArgsConstructor
public class LecturerAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_class_id", nullable = false)
    private StudentClass studentClass;

    @Column(name = "assignee_public_id", nullable = false)
    private UUID assigneePublicId;

    @Column(nullable = false)
    private UUID tenantId;
}
