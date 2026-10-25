package com.pte.practice.internal.dto.response;

import java.util.List;

/**
 * Safe runtime metadata; it deliberately contains no answer or reference data. {@code timedAvailable} tells the
 * client whether the active Score Template can pace this task (it is false in the internal catalog and filled in
 * only for the student-facing one).
 */
public record PracticeCatalogTaskResponse(
        String code,
        String displayName,
        String section,
        boolean scored,
        PracticeCatalogAvailability availability,
        String unavailableReason,
        String profileKey,
        Integer profileVersion,
        String rendererKey,
        Integer contractVersion,
        Integer answerSchemaVersion,
        List<String> requiredClientCapabilities,
        String contentStatus,
        String provenance,
        boolean timedAvailable,
        String timedUnavailableReason,
        long answeredQuestionCount,
        Integer averageScore) {

    public PracticeCatalogTaskResponse {
        requiredClientCapabilities = requiredClientCapabilities == null
                ? List.of() : List.copyOf(requiredClientCapabilities);
    }

    /** Compatibility constructor for callers that predate timed availability. */
    public PracticeCatalogTaskResponse(String code, String displayName, String section, boolean scored,
            PracticeCatalogAvailability availability, String unavailableReason, String profileKey,
            Integer profileVersion, String rendererKey, Integer contractVersion, Integer answerSchemaVersion,
            List<String> requiredClientCapabilities, String contentStatus, String provenance) {
        this(code, displayName, section, scored, availability, unavailableReason, profileKey, profileVersion,
                rendererKey, contractVersion, answerSchemaVersion, requiredClientCapabilities, contentStatus,
                provenance, false, null);
    }

    /** Compatibility constructor for callers that predate the per-student task statistics. */
    public PracticeCatalogTaskResponse(String code, String displayName, String section, boolean scored,
            PracticeCatalogAvailability availability, String unavailableReason, String profileKey,
            Integer profileVersion, String rendererKey, Integer contractVersion, Integer answerSchemaVersion,
            List<String> requiredClientCapabilities, String contentStatus, String provenance,
            boolean timedAvailable, String timedUnavailableReason) {
        this(code, displayName, section, scored, availability, unavailableReason, profileKey, profileVersion,
                rendererKey, contractVersion, answerSchemaVersion, requiredClientCapabilities, contentStatus,
                provenance, timedAvailable, timedUnavailableReason, 0, null);
    }

    public PracticeCatalogTaskResponse withTimedAvailability(boolean available, String reason) {
        return new PracticeCatalogTaskResponse(code, displayName, section, scored, availability, unavailableReason,
                profileKey, profileVersion, rendererKey, contractVersion, answerSchemaVersion,
                requiredClientCapabilities, contentStatus, provenance, available, reason, answeredQuestionCount,
                averageScore);
    }

    /**
     * The requesting student's numbers for this task type: distinct questions answered, and the mean score (0-100)
     * of their scored answers, or null while none is scored.
     */
    public PracticeCatalogTaskResponse withStats(long answered, Integer average) {
        return new PracticeCatalogTaskResponse(code, displayName, section, scored, availability, unavailableReason,
                profileKey, profileVersion, rendererKey, contractVersion, answerSchemaVersion,
                requiredClientCapabilities, contentStatus, provenance, timedAvailable, timedUnavailableReason,
                answered, average);
    }
}
