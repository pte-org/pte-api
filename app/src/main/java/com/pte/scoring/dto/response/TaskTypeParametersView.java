package com.pte.scoring.dto.response;

import com.pte.scoring.rubric.FormBand;

import java.util.List;
import java.util.Map;

/**
 * The numeric rubric parameters of one task type in a readable (ACTIVE or RETIRED) rubric version: trait weights in
 * trait order, Form bands (null when the task has no Form criterion), whether each gate trait is on, and, for task
 * types with an Azure-scored trait, the Azure mapping version with its calibration flag and label (null otherwise).
 */
public record TaskTypeParametersView(
        String taskType,
        Map<String, Integer> traitWeights,
        List<FormBand> formBands,
        Map<String, Boolean> gateEnabled,
        String azureMappingVersion,
        Boolean azureMappingCalibrated,
        String azureMappingLabel) {
}
