package com.pte.assessment.dto.response;

/** One task type of the active exam template and how many items one exam uses ("Full 1 exam" preset). */
public record GenerationPresetResponse(String taskType, String section, int maxCount) {
}
