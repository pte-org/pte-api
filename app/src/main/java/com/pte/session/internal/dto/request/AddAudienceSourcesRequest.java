package com.pte.session.internal.dto.request;

import com.pte.session.internal.constant.SessionConstants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AddAudienceSourcesRequest(
        @NotEmpty(message = SessionConstants.AUDIENCE_SOURCES_REQUIRED)
        @Size(max = SessionConstants.MAX_AUDIENCE_SOURCES_PER_REQUEST,
                message = SessionConstants.AUDIENCE_SOURCES_TOO_MANY)
        List<@NotNull(message = SessionConstants.AUDIENCE_SOURCES_REQUIRED) @Valid AudienceSourceRequest> sources) {
}
