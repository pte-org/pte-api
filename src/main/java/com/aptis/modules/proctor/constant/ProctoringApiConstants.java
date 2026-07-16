package com.aptis.modules.proctor.constant;

import com.aptis.common.constant.ApiVersion;

public final class ProctoringApiConstants {

    public static final String PROCTOR_ATTEMPTS = ApiVersion.V1 + "/proctoring/attempts";
    public static final String PROCTOR_SESSIONS = ApiVersion.V1 + "/proctoring/sessions";

    public static final String PATH_FORCE_SUBMIT = "/{attemptId}/force-submit";
    public static final String PATH_EXTEND_TIME = "/{attemptId}/extend-time";
    public static final String PATH_FLAG_VIOLATION = "/{attemptId}/flag-violation";
    public static final String PATH_BROADCAST = "/{examId}/broadcast";

    public static final String AUTHORITY_PROCTOR = "hasAuthority('PROCTOR')";

    private ProctoringApiConstants() {
    }
}
