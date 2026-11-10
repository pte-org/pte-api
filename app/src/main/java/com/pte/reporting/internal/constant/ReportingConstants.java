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
    public static final String SCORE_AGGREGATION_MISSING = "A submitted attempt has no report score aggregation.";
    public static final String REASON_SCORING_INPUTS_MISSING = "SCORING_INPUTS_MISSING";
    public static final String REASON_PUBLICATION_REQUIRES_CLOSED_SESSION =
            "REPORT_PUBLICATION_REQUIRES_CLOSED_SESSION";
    public static final String AUTHENTICATED_PRINCIPAL_REQUIRED = "No authenticated principal";
    /** A Host template whose tested skills also follow the weights it puts on tested sections. */
    public static final String TEMPLATE_POLICY_CUSTOM = "CUSTOM";
    public static final String ROLE_HOST_ADMIN = "HOST_ADMIN";
    public static final String ROLE_EXAMINER = "EXAMINER";
    /** Snapshot schema written since score explanations (v2 adds trait scores, rubric versions and traces). */
    public static final int SNAPSHOT_SCHEMA_VERSION_TRACED = 2;
    /** The first snapshot schema: scores only, no trace; still readable. */
    public static final int SNAPSHOT_SCHEMA_VERSION_LEGACY = 1;
    public static final String SCORING_METHOD_OBJECTIVE = "OBJECTIVE";
    public static final String SCORE_SOURCE_AI = "AI";
    public static final String SCORE_SOURCE_OBJECTIVE = "OBJECTIVE";
    public static final String OVERALL = "OVERALL";
    public static final String SCORE_EXPLANATION_PATH = "/attempts/{attemptPublicId}/score-explanation";

    private ReportingConstants() {
    }
}
