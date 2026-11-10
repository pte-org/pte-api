package com.pte.itembank.internal.generation.dto;

import com.pte.itembank.internal.generation.domain.QuestionGenerationJobItem;

import java.util.UUID;

public record GenerationItemResponse(int index, String stage, UUID questionPublicId, String failureCode,
                                     String failureNote) {

    public static GenerationItemResponse from(QuestionGenerationJobItem item) {
        return new GenerationItemResponse(item.getItemIndex(), item.getStage().name(), item.getQuestionPublicId(),
                item.getFailureCode(), item.getFailureNote());
    }
}
