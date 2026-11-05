package com.pte.scoring.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One platform-wide rubric version holding the FULL numeric parameters of all 22 task types (weights, Form bands,
 * gates, Azure band-mapping version). Lifecycle: DRAFT -> PENDING -> ACTIVE (approval activates) | REJECTED; the old
 * ACTIVE becomes RETIRED. ACTIVE and RETIRED rows are immutable: the service refuses changes and the V32 trigger
 * rejects any UPDATE other than ACTIVE -> RETIRED. Transition rules live in the service; these methods only record
 * the state the service has decided.
 */
@Entity
@Table(name = "scoring_rubric_versions")
@Getter
@org.hibernate.annotations.SQLRestriction("deleted = false")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScoringRubricVersion extends BaseEntity {

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RubricVersionStatus status;

    /** The stored shape is documented on RubricConstants.PARAM_TASK_TYPES; frozen once ACTIVE (service + trigger). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameters", nullable = false)
    private String parameters;

    @Column(name = "catalog_version", nullable = false)
    private int catalogVersion;

    @Column(name = "author_user_public_id", updatable = false)
    private UUID authorUserPublicId;

    @Column(name = "approver_user_public_id")
    private UUID approverUserPublicId;

    @Column(name = "rejection_reason", length = 1000)
    private String rejectionReason;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    public enum RubricVersionStatus {
        DRAFT, PENDING, ACTIVE, REJECTED, RETIRED
    }

    public static ScoringRubricVersion draft(int versionNo, String parametersJson, int catalogVersion,
                                             UUID authorUserPublicId) {
        ScoringRubricVersion version = new ScoringRubricVersion();
        version.versionNo = versionNo;
        version.parameters = parametersJson;
        version.catalogVersion = catalogVersion;
        version.authorUserPublicId = authorUserPublicId;
        version.status = RubricVersionStatus.DRAFT;
        return version;
    }

    public void markSubmitted(Instant now) {
        status = RubricVersionStatus.PENDING;
        submittedAt = now;
    }

    public void markActive(UUID approverPublicId, Instant now) {
        status = RubricVersionStatus.ACTIVE;
        approverUserPublicId = approverPublicId;
        decidedAt = now;
        activatedAt = now;
    }

    public void markRejected(UUID approverPublicId, String reason, Instant now) {
        status = RubricVersionStatus.REJECTED;
        approverUserPublicId = approverPublicId;
        rejectionReason = reason;
        decidedAt = now;
    }

    public boolean isImmutable() {
        return status == RubricVersionStatus.ACTIVE || status == RubricVersionStatus.RETIRED;
    }

    public void reviseDraft(String parametersJson, int currentCatalogVersion) {
        parameters = parametersJson;
        catalogVersion = currentCatalogVersion;
        status = RubricVersionStatus.DRAFT;
        rejectionReason = null;
        approverUserPublicId = null;
        submittedAt = null;
        decidedAt = null;
    }
}
