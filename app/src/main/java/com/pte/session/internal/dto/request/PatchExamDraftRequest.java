package com.pte.session.internal.dto.request;

import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.FormMode;
import com.pte.session.domain.enums.ReusePolicy;
import com.pte.session.internal.constant.SessionConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PatchExamDraftRequest(
        String name,
        UUID templatePublicId,
        UUID subscriptionPublicId,
        Instant opensAt,
        Instant closesAt,
        ExamMode examMode,
        FormMode formMode,
        ReusePolicy reusePolicy,
        String seriesKey,
        Integer capacity,
        Long expectedVersion,
        @Size(min = 1, max = 4, message = SessionConstants.SKILLS_SIZE_INVALID) List<String> selectedSkills,
        @Min(value = 0, message = SessionConstants.RETRY_COUNT_INVALID)
        @Max(value = 9, message = SessionConstants.RETRY_COUNT_INVALID) Integer maxRetriesPerStudent) {

    /** Source-compatible constructor for callers predating explicit skill/retry configuration. */
    public PatchExamDraftRequest(String name, UUID templatePublicId, UUID subscriptionPublicId,
            Instant opensAt, Instant closesAt, ExamMode examMode, FormMode formMode,
            ReusePolicy reusePolicy, String seriesKey, Integer capacity, Long expectedVersion) {
        this(name, templatePublicId, subscriptionPublicId, opensAt, closesAt, examMode, formMode, reusePolicy,
                seriesKey, capacity, expectedVersion, null, null);
    }
}
