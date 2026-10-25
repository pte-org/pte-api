package com.pte.enrollment.domain;

import com.pte.enrollment.domain.enums.ClassStatus;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * The leaf level of a Host's academic hierarchy ("Lớp"), under a {@link Program}
 * (Khối/Khóa); its students and lecturers are the {@link ClassStudent} and
 * {@link ClassLecturer} child tables. Named {@code TrainingClass} rather than
 * {@code Class}: that name would shadow {@code java.lang.Class} and is a keyword
 * in HQL. {@code organizationId} is the owning organization's public id, copied
 * from the program, so ownership checks need no join through the program.
 */
@Entity
@Table(name = "classes", indexes = {
        @Index(name = "idx_classes_program", columnList = "program_id"),
        @Index(name = "idx_classes_organization", columnList = "organization_id")
})
@Getter
@Setter
@NoArgsConstructor
public class TrainingClass extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ClassStatus status = ClassStatus.ACTIVE;

    /** Places the class under a program and takes the owning organization from it. */
    public void setProgram(Program program) {
        this.program = program;
        if (program != null && program.getOrganization() != null) {
            this.organizationId = program.getOrganization().getPublicId();
        }
    }
}
