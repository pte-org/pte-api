package com.pte.itembank.internal.generation.review;

import com.pte.itembank.domain.enums.CefrLevel;
import java.util.Map;

/**
 * Result of a review. Verdict is PASS or FAIL. Checks map to specific evaluation areas.
 * If any check has ok=false, or if estimated CEFR differs by 2+ bands from target, verdict is FAIL.
 */
public record ReviewVerdict(
    String verdict, // "PASS" or "FAIL"
    Map<String, CheckResult> checks,
    String fixSuggestion) {

    /** One check; {@code estimated} is the CEFR level the reviewer judged (only set on {@code cefrFit}). */
    public record CheckResult(boolean ok, String note, String estimated) {

        public CheckResult(boolean ok, String note) {
            this(ok, note, null);
        }
    }

    public boolean isPassed() {
        return "PASS".equalsIgnoreCase(verdict);
    }

    public boolean isEstimatedCefrOutOfBands(CefrLevel target) {
        CheckResult cefrCheck = checks.get("cefrFit");
        if (cefrCheck == null) {
            return false;
        }
        try {
            String estimatedStr = cefrCheck.estimated() != null && !cefrCheck.estimated().isBlank()
                    ? cefrCheck.estimated().trim().toUpperCase(java.util.Locale.ROOT)
                    : cefrCheck.note() == null ? null : extractCefrLevel(cefrCheck.note());
            if (estimatedStr != null) {
                CefrLevel estimated = CefrLevel.valueOf(estimatedStr);
                int diff = Math.abs(target.ordinal() - estimated.ordinal());
                return diff >= 2;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return false;
    }

    private String extractCefrLevel(String note) {
        for (CefrLevel level : CefrLevel.values()) {
            if (note.contains(level.name())) {
                return level.name();
            }
        }
        return null;
    }
}
