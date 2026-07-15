package com.aptis.modules.examdelivery.constant;

import com.aptis.common.constant.ApiVersion;

public final class ExamDeliveryApiConstants {

    public static final String EXAM_ATTEMPTS = ApiVersion.V1 + "/exam-attempts";

    public static final String PATH_SECTIONS = "/{attemptId}/sections";
    public static final String PATH_SECTION_QUESTIONS = "/{attemptId}/sections/{skill}/questions";

    public static final String AUTHORITY_STUDENT = "hasAuthority('STUDENT')";

    private ExamDeliveryApiConstants() {
    }
}
