package com.pte.session.dto.response;

public record CompositionItemResponse(String taskType, String section, int orderIndex, Integer timingOverrideSeconds,
        Integer maxPlayCount) {
}
