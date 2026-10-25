package com.pte.practice.internal.dto.response;

import com.pte.practice.internal.domain.enums.PracticeSessionSourceType;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.domain.enums.PracticeTimingMode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Student-safe overview/resume projection for a standalone practice session. {@code itemResult} is filled only
 * in the response to an answer, so the client sees the score of the item it just submitted.
 */
public record PracticeSessionResponse(
        UUID publicId,
        PracticeSessionSourceType sourceType,
        String productCode,
        String title,
        UUID organizationId,
        String catalogVersion,
        int timeLimitSeconds,
        PracticeSessionStatus status,
        long version,
        Instant startedAt,
        Instant deadlineAt,
        Instant completedAt,
        Instant discardedAt,
        boolean saveAndExitAvailable,
        boolean canAdvance,
        String nextAction,
        int answeredItemCount,
        int totalItemCount,
        List<PracticeCatalogSectionResponse> sections,
        PracticeTaskResponse currentTask,
        PracticeItemResultResponse itemResult,
        PracticeTimingMode timingMode,
        Integer questionCount,
        /** TASK, SKILL or MOCK_TEST; sessions before V134 read as TASK. */
        String kind,
        String skillCode,
        /** The task type when the session covers exactly one; null otherwise. */
        String taskCode) {

    public PracticeSessionResponse {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }

    public PracticeSessionResponse withItemResult(PracticeItemResultResponse result) {
        return new PracticeSessionResponse(publicId, sourceType, productCode, title, organizationId, catalogVersion,
                timeLimitSeconds, status, version, startedAt, deadlineAt, completedAt, discardedAt,
                saveAndExitAvailable, canAdvance, nextAction, answeredItemCount, totalItemCount, sections,
                currentTask, result, timingMode, questionCount, kind, skillCode, taskCode);
    }
}
