package com.pte.itembank.internal.mapper;

import com.pte.itembank.domain.QuestionTypeDefinition;
import com.pte.itembank.dto.response.QuestionTypeResponse;

public final class QuestionTypeMapper {

    private QuestionTypeMapper() {
    }

    public static QuestionTypeResponse toResponse(QuestionTypeDefinition definition) {
        return new QuestionTypeResponse(
                definition.getPublicId(),
                definition.getCode(),
                definition.getDisplayName(),
                definition.getShortName(),
                definition.getSection().name(),
                definition.isScored(),
                definition.isActive(),
                definition.getDisplayOrder(),
                definition.isRequiresAudioPrompt(),
                definition.isRequiresImagePrompt(),
                definition.isRequiresPromptText(),
                definition.isRequiresOptions(),
                definition.isRequiresCorrectAnswer(),
                definition.isRequiresWordCount(),
                definition.isRequiresSingleCorrectOption(),
                definition.isUsesOptionOrderAsCorrectPosition());
    }
}
