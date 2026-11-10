package com.pte.scoring.internal.rubric.dto;

import java.util.List;

/**
 * The Azure-to-Pearson band mapping table a version uses. {@code calibrated} is false and {@code label} says "chưa
 * hiệu chỉnh với dữ liệu người chấm" until the thresholds are checked against human raters; the thresholds and the
 * input rules ({@code pronunciationInput}, {@code oralFluencyInput}) say how Azure's 0-100 scores become 0-5 bands.
 * The first three fields keep their earlier meaning; the rest were added in phase 5 and are empty when the code no
 * longer declares the table.
 */
public record AzureMappingResponse(
        String version,
        boolean calibrated,
        String label,
        String pronunciationInput,
        String oralFluencyInput,
        List<AzureBandThresholdResponse> pronunciationBands,
        List<AzureBandThresholdResponse> oralFluencyBands) {
}
