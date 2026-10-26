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
 * A Lecturer assigned to supervise a {@link TrainingClass} â€” "just like a
 * Proctor," mirrors {@code scheduling.ExamSessionProctor}'s shape exactly:
 * target entity + bare {@code assigneePublicId} (iam's {@code publicId},
 * never a cross-service FK) + denormalized {@code organizationId}. No role
 * sub-enum, unlike {@code ExamSessionProctor} â€” the feature spec doesn't ask
 * for a Lecturer sub-classification.
 */
@Entity
@Table(name = "class_lecturers", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"class_id", "assignee_public_id"})
}, indexes = {
        @Index(name = "idx_class_lecturers_class", columnList = "class_id")
})
@Getter
@Setter
@NoArgsConstructor
public class ClassLecturer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id", nullable = false)
    private TrainingClass trainingClass;

    @Column(name = "assignee_public_id", nullable = false)
    private UUID assigneePublicId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;
}
