package com.pte.assessment.internal.constant;

import com.pte.shared.security.SecurityRoles;

import java.util.List;

/** Path and roles of the AI-generation presets read (the active template's task types and counts). */
public final class GenerationPresetConstants {

    public static final String GENERATION_PRESETS_PATH = "/api/v1/question-generation/presets";

    /** The active template's CEFR mix, which the "full exam" preset spreads its questions over. */
    public static final String GENERATION_CEFR_MIX_PATH = GENERATION_PRESETS_PATH + "/cefr-mix";

    /**
     * The platform bank authors, the same roles as AI question generation (itembank's generation access policy;
     * an itembank test keeps both lists equal, since itembank internals cannot be imported here).
     */
    public static final List<String> GENERATION_PRESET_ROLES = List.of(SecurityRoles.PLATFORM_ADMIN,
            SecurityRoles.ACADEMIC_MANAGER, SecurityRoles.ACADEMIC_STAFF, SecurityRoles.LEGACY_PLATFORM_AUTHOR);

    /** {@link #GENERATION_PRESET_ROLES} as a method-security expression (annotations need a constant). */
    public static final String GENERATION_PRESET_AUTHORITY =
            "hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')";

    private GenerationPresetConstants() {
    }
}
