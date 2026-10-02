package com.pte.session.internal.dto.request;

import com.pte.session.domain.enums.AudienceSourceType;
import com.pte.session.internal.constant.SessionConstants;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AudienceSourceRequest(
        @NotNull(message = SessionConstants.AUDIENCE_SOURCE_TYPE_REQUIRED) AudienceSourceType sourceType,
        @NotNull(message = SessionConstants.AUDIENCE_SOURCE_REFERENCE_REQUIRED) UUID sourcePublicId) {
}
