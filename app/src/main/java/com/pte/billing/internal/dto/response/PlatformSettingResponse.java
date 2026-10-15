package com.pte.billing.internal.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PlatformSettingResponse(
        UUID publicId,
        String key,
        String value,
        String description,
        String category,
        String type,
        String defaultValue,
        String unit,
        Integer minimum,
        Integer maximum,
        boolean editable,
        boolean requiresRestart,
        String scope,
        String impact,
        Instant updatedAt) {
}
