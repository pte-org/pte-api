package com.pte.itembank;

import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.internal.exception.InvalidQuestionTypeException;

import java.util.Locale;
import java.util.Map;

/**
 * Canonical task-code boundary for the shared item bank.
 *
 * <p>The persisted/API code is the {@link PteTaskType} name. The three aliases
 * are accepted only while reading older requests, rows or snapshots created
 * before V40. New catalog writes must use {@link #requireCanonicalCode(String)}
 * so the legacy vocabulary cannot be reintroduced.</p>
 */
public final class TaskTypeCodeCompatibility {

    private static final Map<String, String> LEGACY_TO_CANONICAL = Map.of(
            "FILL_BLANKS_READING_WRITING", "FILL_IN_THE_BLANKS_DROPDOWN",
            "FILL_BLANKS_READING", "FILL_IN_THE_BLANKS_DRAG_AND_DROP",
            "FILL_BLANKS_LISTENING", "FILL_IN_THE_BLANKS_TYPE_IN");

    private TaskTypeCodeCompatibility() {
    }

    /**
     * Converts a known legacy alias to its canonical code and validates that
     * the result is a current standard PTE task type.
     */
    public static String canonicalize(String rawCode) {
        String normalized = normalize(rawCode);
        String canonical = LEGACY_TO_CANONICAL.getOrDefault(normalized, normalized);
        try {
            PteTaskType.valueOf(canonical);
            return canonical;
        } catch (IllegalArgumentException ex) {
            throw new InvalidQuestionTypeException();
        }
    }

    /**
     * Validates a code for a new catalog row. Legacy aliases are deliberately
     * rejected so all newly persisted/API-visible rows stay canonical.
     */
    public static String requireCanonicalCode(String rawCode) {
        String normalized = normalize(rawCode);
        if (LEGACY_TO_CANONICAL.containsKey(normalized)) {
            throw new InvalidQuestionTypeException();
        }
        return canonicalize(normalized);
    }

    /** Reads canonical and known pre-V40 values into the enum used by runtime code. */
    public static PteTaskType parse(String rawCode) {
        return PteTaskType.valueOf(canonicalize(rawCode));
    }

    /** Returns an upper-case lookup value without rejecting an unknown persisted code. */
    public static String normalizeForLookup(String rawCode) {
        String normalized = normalize(rawCode);
        return LEGACY_TO_CANONICAL.getOrDefault(normalized, normalized);
    }

    public static boolean isLegacyAlias(String rawCode) {
        return LEGACY_TO_CANONICAL.containsKey(normalize(rawCode));
    }

    private static String normalize(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new InvalidQuestionTypeException();
        }
        return rawCode.trim().toUpperCase(Locale.ROOT);
    }
}
