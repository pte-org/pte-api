package com.pte.reporting.domain;

/** Stable invariant messages for report publication state. */
public final class ReportingDomainConstants {

    public static final String REPORT_SNAPSHOT_REQUIRED = "snapshotJson is required";
    public static final String PUBLICATION_REFERENCE_REQUIRED = "publicationPublicId is required";
    public static final String PUBLISHER_REFERENCE_REQUIRED = "publishedByPublicId is required";
    public static final String PUBLICATION_COHORT_SIZE_INVALID = "cohortSize must be positive";
    public static final String PUBLICATION_TIME_REQUIRED = "publishedAt is required";

    private ReportingDomainConstants() {
    }
}
