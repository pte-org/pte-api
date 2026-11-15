package com.pte.billing.internal.constant;

import com.pte.billing.QuestionGenerationLimit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Allowlisted metadata for settings that are safe to expose to platform
 * administrators. Deployment properties and deprecated database rows stay out
 * of this catalog until their runtime semantics are explicitly implemented.
 */
public final class PlatformSettingCatalog {

    public static final String CATEGORY_COMMERCIAL = "COMMERCIAL";
    public static final String CATEGORY_AI = "AI";
    public static final String TYPE_INTEGER = "INTEGER";
    public static final String TYPE_DURATION_HOURS = "DURATION_HOURS";
    public static final String UNIT_STUDENTS = "students";
    public static final String UNIT_HOURS = "hours";
    public static final String SCOPE_FUTURE_TENANT_APPROVAL = "FUTURE_TENANT_APPROVAL";
    public static final String SCOPE_FUTURE_APPLICATION_NOTIFICATION = "FUTURE_APPLICATION_NOTIFICATION";
    public static final String SCOPE_NEXT_GENERATED_QUESTION = "NEXT_GENERATED_QUESTION";

    private static final Definition FREE_STUDENT_LIMIT = new Definition(
            BillingConstants.FREE_STUDENT_LIMIT_SETTING_KEY,
            CATEGORY_COMMERCIAL,
            TYPE_INTEGER,
            "50",
            UNIT_STUDENTS,
            0,
            null,
            true,
            false,
            SCOPE_FUTURE_TENANT_APPROVAL,
            "Applies to tenant applications approved after this value is saved.");

    private static final Definition TENANT_APPLICATION_RESPONSE_SLA_HOURS = new Definition(
            BillingConstants.TENANT_APPLICATION_RESPONSE_SLA_HOURS_SETTING_KEY,
            CATEGORY_COMMERCIAL,
            TYPE_DURATION_HOURS,
            String.valueOf(BillingConstants.DEFAULT_TENANT_APPLICATION_RESPONSE_SLA_HOURS),
            UNIT_HOURS,
            1,
            null,
            true,
            false,
            SCOPE_FUTURE_APPLICATION_NOTIFICATION,
            "Applies to application notifications sent after this value is saved.");

    private static final List<Definition> ACTIVE_DEFINITIONS = Stream.concat(
            Stream.of(FREE_STUDENT_LIMIT, TENANT_APPLICATION_RESPONSE_SLA_HOURS),
            Arrays.stream(QuestionGenerationLimit.values()).map(PlatformSettingCatalog::generationLimit)).toList();

    private static final Map<String, Definition> DEFINITIONS = ACTIVE_DEFINITIONS.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(Definition::key, definition -> definition));

    private PlatformSettingCatalog() {
    }

    /** A question generation limit, edited on the AI overview page; it applies to jobs and questions started later. */
    private static Definition generationLimit(QuestionGenerationLimit limit) {
        return new Definition(limit.key(), CATEGORY_AI, TYPE_INTEGER, String.valueOf(limit.defaultValue()), limit.unit(),
                limit.minimum(), limit.maximum(), true, false, SCOPE_NEXT_GENERATED_QUESTION,
                limit.description() + "; applies to jobs started after this value is saved.");
    }

    public static List<Definition> activeDefinitions() {
        return ACTIVE_DEFINITIONS;
    }

    public static Definition find(String key) {
        return DEFINITIONS.get(key);
    }

    public record Definition(
            String key,
            String category,
            String type,
            String defaultValue,
            String unit,
            Integer minimum,
            Integer maximum,
            boolean editable,
            boolean requiresRestart,
            String scope,
            String impact) {
    }
}
