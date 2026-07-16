package com.aptis.modules.questionbank.constant;

import com.aptis.common.constant.ApiVersion;
import com.aptis.modules.questionbank.domain.enums.Skill;

public final class QuestionBankApiConstants {

    // Base paths
    public static final String QUESTIONS = ApiVersion.V1 + "/questions";
    public static final String OPTIONS = ApiVersion.V1 + "/options";

    // Sub-paths used within controllers
    public static final String PATH_BY_ID = "/{id}";
    public static final String PATH_PUBLISH = "/{id}/publish";
    public static final String PATH_AUDIO = "/{id}/audio";

    // Spring Security expressions for @PreAuthorize
    public static final String AUTHORITY_ADMIN = "hasAuthority('ADMIN')";
    public static final String AUTHORITY_HOST = "hasAuthority('HOST')";
    public static final String AUTHORITY_ADMIN_OR_HOST = "hasAuthority('ADMIN') or hasAuthority('HOST')";

    // Audio upload constraints (Listening/Speaking question authoring)
    public static final long AUDIO_MAX_SIZE_BYTES = 50L * 1024 * 1024;
    public static final java.util.Set<String> AUDIO_ALLOWED_MIME_TYPES =
            java.util.Set.of("audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp4");

    // Bulk import (Excel + optional audio ZIP) — flat MCQ, Host tenants only
    public static final String PATH_IMPORT = "/import";
    public static final String EXCEL_EXTENSION_XLSX = ".xlsx";
    public static final long IMPORT_MAX_EXCEL_SIZE_BYTES = 10L * 1024 * 1024;
    public static final long IMPORT_MAX_ZIP_SIZE_BYTES = 100L * 1024 * 1024;
    public static final int IMPORT_MAX_ROWS = 1000;
    public static final java.util.List<String> IMPORT_REQUIRED_HEADERS =
            java.util.List.of("question_text", "skill_type", "correct_answer", "distractor_1");
    public static final java.util.Set<Skill> IMPORT_ALLOWED_SKILLS =
            java.util.Set.of(Skill.READING, Skill.LISTENING);
    public static final String IMPORT_DEFAULT_DIFFICULTY = "B1";

    private QuestionBankApiConstants() {
    }
}
