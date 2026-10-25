package com.pte.practice.internal.dto.response;

import java.util.UUID;

/**
 * A mock test as listed to a student: the fixed test plus the student's latest attempt at it (the latest one that was
 * not discarded), or null when they have not started it.
 */
public record PracticeMockTestResponse(
        UUID publicId,
        String title,
        int sequenceNo,
        int itemCount,
        int estimatedMinutes,
        LatestAttempt latestAttempt) {

    public record LatestAttempt(
            UUID sessionPublicId,
            String status,
            int answeredItemCount,
            int totalItemCount,
            Integer estimatedOverall) {
    }

    public PracticeMockTestResponse withoutAttempt() {
        return new PracticeMockTestResponse(publicId, title, sequenceNo, itemCount, estimatedMinutes, null);
    }
}
