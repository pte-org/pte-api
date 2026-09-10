package com.pte.admin.domain;

import com.pte.admin.domain.enums.ClassStatus;
import com.pte.common.domain.BaseEntity;
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

/**
 * The leaf level of a Host's academic hierarchy ("Lớp"), under a {@link Program}
 * (Khối/Khóa). Named {@code StudentClass} rather than {@code Class} — that name
 * would shadow {@code java.lang.Class} in every file that imports it.
 */
@Entity
@Table(name = "student_classes", indexes = {@Index(name = "idx_student_classes_program", columnList = "program_id")})
@Getter
@Setter
@NoArgsConstructor
public class StudentClass extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ClassStatus status = ClassStatus.ACTIVE;
}
