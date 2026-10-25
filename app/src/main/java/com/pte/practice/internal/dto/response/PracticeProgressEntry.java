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
        /** Mean score (0-100) of the session's scored answers; null while none is scored. */
        Double score,
        /** TASK, SKILL or MOCK_TEST; sessions before V134 read as TASK. */
        String kind,
        String skillCode,
        /** The task type when the session covers exactly one; null otherwise. */
        String taskCode) {
}
