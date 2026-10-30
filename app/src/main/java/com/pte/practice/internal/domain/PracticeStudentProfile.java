package com.pte.practice.internal.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Practice-specific student profile: stores student learning targets such as their planned exam date.
 */
@Entity
@Table(name = "practice_student_profiles")
@Getter
@Setter
@NoArgsConstructor
public class PracticeStudentProfile extends BaseEntity {

    @Column(name = "student_public_id", nullable = false, unique = true)
    private UUID studentPublicId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "exam_date")
    private LocalDate examDate;

    @Version
    private Long version;
}
