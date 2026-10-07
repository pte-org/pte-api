package com.pte.practice.internal.dto.response;

import com.pte.practice.internal.domain.enums.PracticeSessionSourceType;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Student-safe overview/resume projection for a standalone practice session. */
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
        List<PracticeCatalogSectionResponse> sections) {

    public PracticeSessionResponse {
        sections = sections == null ? List.of() : List.copyOf(sections);
    }
}
