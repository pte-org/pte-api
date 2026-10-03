package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.GradingCohortStatus;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.internal.constant.GradingConstants;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Frozen post-CLOSED grading membership and policy for one session. */
@Entity
@Table(name = "session_grading_cohorts", indexes = {
        @Index(name = "idx_grading_cohort_status", columnList = "status")
}, uniqueConstraints = @UniqueConstraint(name = "uq_grading_cohort_session",
        columnNames = {"tenant_id", "session_public_id"}))
@Getter
@NoArgsConstructor
public class GradingCohort extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID sessionPublicId;

    @Column(nullable = false)
    private UUID createdByPublicId;

    @Column(nullable = false)
    private long cohortVersion = 1L;

    @Column(nullable = false, length = 128)
    private String previewVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private GradingMarkingMode markingMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private GradingCohortStatus status = GradingCohortStatus.FROZEN;

    @Column(nullable = false)
    private Instant frozenAt;

    @Column
    private Instant completedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    public GradingCohort(UUID tenantId, UUID sessionPublicId, UUID createdByPublicId,
            String previewVersion, GradingMarkingMode markingMode, Instant frozenAt) {
        this.tenantId = Objects.requireNonNull(tenantId, GradingConstants.TENANT_REQUIRED);
        this.sessionPublicId = Objects.requireNonNull(sessionPublicId, GradingConstants.SESSION_REQUIRED);
        this.createdByPublicId = Objects.requireNonNull(createdByPublicId, GradingConstants.ACTOR_REQUIRED);
        this.previewVersion = Objects.requireNonNull(previewVersion, GradingConstants.PREVIEW_VERSION_REQUIRED);
        this.markingMode = Objects.requireNonNull(markingMode, GradingConstants.MARKING_MODE_REQUIRED);
        this.frozenAt = Objects.requireNonNull(frozenAt, GradingConstants.FROZEN_AT_REQUIRED);
    }

    public boolean complete(Instant completedAt) {
        Objects.requireNonNull(completedAt, GradingConstants.COMPLETED_AT_REQUIRED);
        if (status == GradingCohortStatus.COMPLETED) {
            return false;
        }
        status = GradingCohortStatus.COMPLETED;
        this.completedAt = completedAt;
        return true;
    }
}
