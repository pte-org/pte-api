package com.pte.session.internal.dto.request;

import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.FormMode;
import com.pte.session.domain.enums.ReusePolicy;
import com.pte.session.internal.constant.SessionConstants;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.util.UUID;

public record CreateExamDraftRequest(
        @NotBlank(message = SessionConstants.SESSION_NAME_REQUIRED) String name,
        @NotNull(message = SessionConstants.TEMPLATE_REFERENCE_REQUIRED) UUID templatePublicId,
        @NotNull(message = SessionConstants.SUBSCRIPTION_REFERENCE_REQUIRED) UUID subscriptionPublicId,
        @NotNull(message = SessionConstants.OPEN_TIME_REQUIRED) @Future(message = SessionConstants.OPEN_TIME_FUTURE) Instant opensAt,
        @NotNull(message = SessionConstants.CLOSE_TIME_REQUIRED) Instant closesAt,
        ExamMode examMode,
        FormMode formMode,
        ReusePolicy reusePolicy,
        String seriesKey,
        @NotNull(message = SessionConstants.CAPACITY_REQUIRED) @Positive(message = SessionConstants.CAPACITY_POSITIVE) Integer capacity) {
}
