package com.aptis.modules.examoperations.constant;

import com.aptis.common.constant.ApiVersion;

public final class ExamOperationsApiConstants {

    // Exam endpoints
    public static final String EXAMS = ApiVersion.V1 + "/exams";

    // Host roster import endpoints
    public static final String HOST_IMPORTS = ApiVersion.V1 + "/host/imports";

    // Sub-paths for roster import controller
    public static final String PATH_BATCH_ID = "/{batchId}";
    public static final String PATH_ASSIGN = "/assign";
    public static final String PATH_EXAMS = "/exams";

    // Spring Security expressions
    public static final String AUTHORITY_HOST = "hasAuthority('HOST')";

    private ExamOperationsApiConstants() {
    }
}
