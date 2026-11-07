package com.pte.itembank.internal.generation.dto;

/** A task type the form can offer, with what the pipeline needs to make it. */
public record GenerationTaskTypeResponse(String taskType, String section, boolean needsAudio, boolean needsImage,
                                         int estimatedSecondsPerItem) {
}
