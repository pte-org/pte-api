package com.pte.billing.internal.mapper;

import com.pte.billing.domain.PlatformSetting;
import com.pte.billing.internal.constant.PlatformSettingCatalog;
import com.pte.billing.internal.dto.response.PlatformSettingResponse;

public final class PlatformSettingMapper {

    private PlatformSettingMapper() {
    }

    public static PlatformSettingResponse toResponse(PlatformSetting setting) {
        PlatformSettingCatalog.Definition definition = PlatformSettingCatalog.find(setting.getKey());
        if (definition == null) {
            return null;
        }
        return toResponse(setting, definition);
    }

    public static PlatformSettingResponse toResponse(
            PlatformSetting setting, PlatformSettingCatalog.Definition definition) {
        return new PlatformSettingResponse(
                setting.getPublicId(),
                setting.getKey(),
                setting.getValue(),
                setting.getDescription(),
                definition.category(),
                definition.type(),
                definition.defaultValue(),
                definition.unit(),
                definition.minimum(),
                definition.maximum(),
                definition.editable(),
                definition.requiresRestart(),
                definition.scope(),
                definition.impact(),
                setting.getUpdatedAt());
    }
}
