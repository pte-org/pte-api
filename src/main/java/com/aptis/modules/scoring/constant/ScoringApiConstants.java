package com.aptis.modules.scoring.constant;

import com.aptis.common.constant.ApiVersion;

public final class ScoringApiConstants {

    public static final String SCORES = ApiVersion.V1 + "/scores";
    public static final String GRADER_BASE = ApiVersion.V1 + "/grader";
    public static final String GRADER_PATH_ATTEMPTS = "/attempts";
    public static final String GRADER_PATH_ATTEMPT_ANSWERS = "/{attemptId}/answers";
    public static final String GRADER_PATH_GRADE_ANSWER = "/{attemptId}/answers/{answerId}";

    public static final String ATTEMPT_NOT_FOUND = "ATTEMPT_NOT_FOUND";
    public static final String ANSWER_NOT_FOUND = "ANSWER_NOT_FOUND";
    public static final String ANSWER_CONCURRENT_MODIFICATION = "ANSWER_CONCURRENT_MODIFICATION";

    private ScoringApiConstants() {
    }
}
