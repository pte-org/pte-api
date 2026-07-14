package com.aptis.modules.questionbank.constant;

import com.aptis.common.constant.ApiVersion;

public final class QuestionBankApiConstants {

    // Base paths
    public static final String QUESTIONS = ApiVersion.V1 + "/questions";
    public static final String OPTIONS = ApiVersion.V1 + "/options";

    // Sub-paths used within controllers
    public static final String PATH_BY_ID = "/{id}";

    // Spring Security expressions for @PreAuthorize
    public static final String AUTHORITY_ADMIN = "hasAuthority('ADMIN')";

    private QuestionBankApiConstants() {
    }
}
