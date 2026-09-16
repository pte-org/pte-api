package com.pte.billing.domain;

import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Two families sharing one table, ON PURPOSE not two: an admin browsing the
 * catalog wants one list, not two. {@code durationDays}/{@code
 * maxStudentsPerSession} only mean anything for {@link PlanType#EXAM_PACKAGE};
 * {@code extraStudentSlots} only for {@link PlanType#STUDENT_CAPACITY} —
 * {@code PlanService} validates the two never mix, this entity does not.
 */
@Entity
@Table(name = "plans")
@Getter
@Setter
@NoArgsConstructor
public class Plan extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PlanType type;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private String currency;

    /** EXAM_PACKAGE only — how long the Subscription this plan activates stays valid. */
    @Column
    private Integer durationDays;

    /** EXAM_PACKAGE only — snapshotted onto the Subscription at activation (Phase 4), never read live after that. */
    @Column
    private Integer maxStudentsPerSession;

    /** STUDENT_CAPACITY only — added straight to tenant.studentLimit via QuotaTransaction, no Subscription involved. */
    @Column
    private Integer extraStudentSlots;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private PlanStatus status = PlanStatus.DRAFT;

    public void activate() {
        this.status = PlanStatus.ACTIVE;
    }

    public void archive() {
        this.status = PlanStatus.ARCHIVED;
    }
}
