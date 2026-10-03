package com.pte.scoring.domain;

import com.pte.shared.domain.BaseEntity;
import com.pte.scoring.internal.constant.GradingConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;
import java.util.UUID;

/** Immutable membership/disposition captured when the host freezes a cohort. */
@Entity
@Table(name = "session_grading_cohort_members", indexes = {
        @Index(name = "idx_grading_member_attempt", columnList = "tenant_id, session_public_id, attempt_public_id")
}, uniqueConstraints = @UniqueConstraint(name = "uq_grading_member_attempt",
        columnNames = {"cohort_public_id", "attempt_public_id"}))
@Getter
@NoArgsConstructor
public class GradingCohortMember extends BaseEntity {

    @Column(nullable = false)
    private UUID cohortPublicId;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID sessionPublicId;

    @Column(nullable = false)
    private UUID attemptPublicId;

    @Column(nullable = false)
    private UUID studentPublicId;

    @Column(nullable = false, length = 16)
    private String attemptStatusSnapshot;

    @Column(nullable = false)
    private boolean excluded;

    @Column(length = 500)
    private String dispositionReason;

    public GradingCohortMember(UUID cohortPublicId, UUID tenantId, UUID sessionPublicId, UUID attemptPublicId,
            UUID studentPublicId, String attemptStatusSnapshot, boolean excluded, String dispositionReason) {
        this.cohortPublicId = Objects.requireNonNull(cohortPublicId, GradingConstants.COHORT_REQUIRED);
        this.tenantId = Objects.requireNonNull(tenantId, GradingConstants.TENANT_REQUIRED);
        this.sessionPublicId = Objects.requireNonNull(sessionPublicId, GradingConstants.SESSION_REQUIRED);
        this.attemptPublicId = Objects.requireNonNull(attemptPublicId, GradingConstants.ATTEMPT_REQUIRED);
        this.studentPublicId = Objects.requireNonNull(studentPublicId, GradingConstants.STUDENT_REQUIRED);
        this.attemptStatusSnapshot = Objects.requireNonNull(attemptStatusSnapshot,
                GradingConstants.ATTEMPT_STATUS_REQUIRED);
        this.excluded = excluded;
        this.dispositionReason = dispositionReason;
    }
}
