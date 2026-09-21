package com.pte.session.internal.dto.request;

import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.FormMode;
import com.pte.session.domain.enums.ReusePolicy;

import java.time.Instant;
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
        Long expectedVersion) {
}
