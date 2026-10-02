package com.pte.session.internal.dto.response;

import com.pte.session.domain.enums.AudienceSourceType;

import java.util.UUID;

public record AudienceSourceResponse(
        UUID publicId,
        AudienceSourceType sourceType,
        UUID sourcePublicId) {
}
