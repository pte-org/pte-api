package com.pte.scheduling.dto.response;

public record CompositionItemResponse(String taskType, String section, int orderIndex, Integer timingOverrideSeconds,
        Integer maxPlayCount) {
}
