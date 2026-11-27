package com.pte.assessment.internal.controller;

import com.pte.assessment.dto.response.GenerationCefrMixResponse;
import com.pte.assessment.dto.response.GenerationPresetResponse;
import com.pte.assessment.internal.constant.GenerationPresetConstants;
import com.pte.assessment.internal.service.GenerationPresetQueryService;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Presets for the AI question-generation popup. The URL sits under {@code /question-generation} for the author UI,
 * but the endpoint is owned by assessment: the template lives here and assessment already depends on itembank, so
 * itembank reading templates would create a module cycle. Platform bank authors only (same roles as generation).
 */
@RestController
public class GenerationPresetController {

    private final GenerationPresetQueryService presets;

    public GenerationPresetController(GenerationPresetQueryService presets) {
        this.presets = presets;
    }

    @GetMapping(GenerationPresetConstants.GENERATION_PRESETS_PATH)
    @PreAuthorize(GenerationPresetConstants.GENERATION_PRESET_AUTHORITY)
    public ApiResponse<List<GenerationPresetResponse>> presets() {
        return ApiResponse.success(presets.listActivePresets());
    }

    /** Null data when there is no active template or it sets no CEFR mix: the popup then keeps one level. */
    @GetMapping(GenerationPresetConstants.GENERATION_CEFR_MIX_PATH)
    @PreAuthorize(GenerationPresetConstants.GENERATION_PRESET_AUTHORITY)
    public ApiResponse<GenerationCefrMixResponse> cefrMix() {
        return ApiResponse.success(presets.findActiveCefrMix().orElse(null));
    }
}
