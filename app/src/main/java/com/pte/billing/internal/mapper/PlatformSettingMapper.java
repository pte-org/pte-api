package com.pte.billing.internal.mapper;

import com.pte.billing.domain.PlatformSetting;
import com.pte.billing.internal.dto.response.PlatformSettingResponse;

public final class PlatformSettingMapper {

    private PlatformSettingMapper() {
    }

    public static PlatformSettingResponse toResponse(PlatformSetting setting) {
        return new PlatformSettingResponse(
                setting.getPublicId(),
                setting.getKey(),
                setting.getValue(),
                setting.getDescription());
    }
}
