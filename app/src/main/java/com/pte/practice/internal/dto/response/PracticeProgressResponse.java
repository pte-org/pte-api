package com.pte.practice.internal.dto.response;

import java.time.Instant;
import java.util.List;

/** Read-only, student-scoped practice history projection. */
public record PracticeProgressResponse(
        List<PracticeProgressEntry> entries,
        int totalSessions,
        int completedSessions,
        int answeredItems,
        int totalItems,
        Instant generatedAt,
        boolean hasMore) {

    public PracticeProgressResponse {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
