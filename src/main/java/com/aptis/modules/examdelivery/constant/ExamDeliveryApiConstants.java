package com.aptis.modules.examdelivery.constant;

import com.aptis.common.constant.ApiVersion;

public final class ExamDeliveryApiConstants {

    public static final String EXAM_ATTEMPTS = ApiVersion.V1 + "/exam-attempts";

    public static final String PATH_SECTIONS = "/{attemptId}/sections";
    public static final String PATH_SECTION_QUESTIONS = "/{attemptId}/sections/{skill}/questions";
    public static final String PATH_HEARTBEAT = "/{attemptId}/heartbeat";
    public static final String PATH_SECTION_SWITCH = "/{attemptId}/section-switch";
    public static final String PATH_SUBMIT = "/{attemptId}/submit";
    public static final String PATH_RETRY_REQUEST = "/{attemptId}/retry-request";

    public static final String RETRY_REQUESTS = ApiVersion.V1 + "/retry-requests";
    public static final String PATH_RETRY_PENDING = "/pending";
    public static final String PATH_RETRY_APPROVE = "/{requestId}/approve";
    public static final String PATH_RETRY_REJECT = "/{requestId}/reject";

    public static final String AUTHORITY_STUDENT = "hasAuthority('STUDENT')";
    public static final String AUTHORITY_HOST_OR_GRADER = "hasAnyAuthority('HOST', 'GRADER')";

    private ExamDeliveryApiConstants() {
    }
}
