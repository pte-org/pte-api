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
 * the published result of one attempt (10-90 skill scores): whether it is
 * visible yet. Created lazily (host viewing before publish, or the publish
 * command itself) from attempt's canonical {@code AttemptSummaryView} —
 * {@code published} is the ONLY field this module decides for itself, the
 * visibility gate; everything else here is a stable copy of what attempt
 * already owns.
 */
@Entity
@Table(name = "exam_results", indexes = {
        @Index(name = "idx_exam_results_session", columnList = "session_public_id"),
        @Index(name = "idx_exam_results_student_tenant_published",
                columnList = "student_public_id, tenant_id, published, published_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ExamResult extends BaseEntity {

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

    /** Published 10-90 scores; null means the skill (or the overall) had no scored answer to base a score on. */
    private Integer overallScore;

    private Integer listeningScore;

    private Integer readingScore;

    private Integer speakingScore;

    private Integer writingScore;

    /** Copies the published scores next to the snapshot so lists and filters need not parse the snapshot. */
    public void recordScores(Integer overall, Integer listening, Integer reading, Integer speaking, Integer writing) {
        this.overallScore = overall;
        this.listeningScore = listening;
        this.readingScore = reading;
        this.speakingScore = speaking;
        this.writingScore = writing;
    }

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
