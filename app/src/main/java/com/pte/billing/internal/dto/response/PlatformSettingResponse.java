package com.pte.billing.internal.dto.response;

import java.util.UUID;

public record PlatformSettingResponse(
        UUID publicId,
        String key,
        String value,
        String description) {
}
