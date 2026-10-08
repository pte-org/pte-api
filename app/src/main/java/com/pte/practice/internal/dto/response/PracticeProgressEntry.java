package com.pte.practice.internal.dto.response;

import com.pte.practice.internal.domain.enums.PracticeProgressStatus;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;

import java.time.Instant;
import java.util.UUID;

/** Student-owned history row; it intentionally contains no organization data. */
public record PracticeProgressEntry(
        UUID sessionPublicId,
        String productCode,
        String title,
        PracticeProgressStatus status,
        PracticeSessionStatus sessionStatus,
        Instant startedAt,
        Instant completedAt,
        Instant lastActivityAt,
        int answeredItemCount,
        int draftItemCount,
        int skippedItemCount,
        int totalItemCount,
        int lowConfidenceCount,
        int mediumConfidenceCount,
        int highConfidenceCount,
        Double score) {
}
