package com.pte.session.internal.dto.response.audience;

import java.util.List;

public record SessionAudienceResponse(
        List<AudienceSourceResponse> sources,
        AudiencePreviewResponse preview) {
}
