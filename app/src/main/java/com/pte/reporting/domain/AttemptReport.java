package com.pte.reporting.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * reporting's own bounded fact about one attempt: whether its report is
 * visible yet. Created lazily (host viewing before publish, or the publish
 * command itself) from attempt's canonical {@code AttemptSummaryView} —
 * {@code published} is the ONLY field this module decides for itself, the
 * visibility gate; everything else here is a stable copy of what attempt
 * already owns.
 */
@Entity
@Table(name = "attempt_reports", indexes = {
        @Index(name = "idx_attempt_reports_session", columnList = "session_public_id")
})
@Getter
@Setter
@NoArgsConstructor
public class AttemptReport extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID attemptPublicId;

    @Column(nullable = false)
    private UUID sessionPublicId;

    @Column(nullable = false)
    private UUID studentPublicId;

    @Column(nullable = false)
    private UUID tenantId;

    /** Source snapshot provenance used to apply its immutable section weights. */
    @Column
    private UUID snapshotPublicId;

    @Column(nullable = false)
    private boolean published = false;

    @Column
    private Instant publishedAt;

    @Column(name = "report_snapshot_json", columnDefinition = "text")
    private String reportSnapshotJson;

    @Column(name = "publication_public_id")
    private UUID publicationPublicId;

    @Column(name = "published_by_public_id")
    private UUID publishedByPublicId;

    @Column(name = "publication_cohort_size")
    private Integer publicationCohortSize;

    public void publishSnapshot(String snapshotJson, UUID publicationPublicId, UUID publishedByPublicId,
            int cohortSize, Instant publishedAt) {
        this.reportSnapshotJson = java.util.Objects.requireNonNull(snapshotJson,
                ReportingDomainConstants.REPORT_SNAPSHOT_REQUIRED);
        this.publicationPublicId = java.util.Objects.requireNonNull(publicationPublicId,
                ReportingDomainConstants.PUBLICATION_REFERENCE_REQUIRED);
        this.publishedByPublicId = java.util.Objects.requireNonNull(publishedByPublicId,
                ReportingDomainConstants.PUBLISHER_REFERENCE_REQUIRED);
        if (cohortSize < 1) {
            throw new IllegalArgumentException(ReportingDomainConstants.PUBLICATION_COHORT_SIZE_INVALID);
        }
        this.publicationCohortSize = cohortSize;
        this.publishedAt = java.util.Objects.requireNonNull(publishedAt,
                ReportingDomainConstants.PUBLICATION_TIME_REQUIRED);
        this.published = true;
    }
}
