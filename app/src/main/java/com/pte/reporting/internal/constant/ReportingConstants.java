package com.pte.reporting.internal.constant;

/** Centralized codes/labels for reporting. */
public final class ReportingConstants {

    public static final String REPORT_NOT_FOUND = "REPORT_NOT_FOUND";
    public static final String PUBLICATION_NOT_READY = "REPORT_PUBLICATION_NOT_READY";
    public static final String PUBLICATION_NOT_READY_MESSAGE =
            "Reports cannot be published until every required score is ready.";
    public static final String SNAPSHOT_SERIALIZATION_FAILED = "Unable to serialize the immutable report snapshot.";
    public static final String SNAPSHOT_UNAVAILABLE = "The immutable report snapshot is unavailable.";
    public static final String SNAPSHOT_VERSION_UNSUPPORTED = "The report snapshot version is not supported.";
    public static final String REASON_SCORING_INPUTS_MISSING = "SCORING_INPUTS_MISSING";
    public static final String REASON_PUBLICATION_REQUIRES_CLOSED_SESSION =
            "REPORT_PUBLICATION_REQUIRES_CLOSED_SESSION";
    public static final String AUTHENTICATED_PRINCIPAL_REQUIRED = "No authenticated principal";

    private ReportingConstants() {
    }
}
