package com.pte.itembank.internal.mapper;

import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.domain.QuestionTypeDefinition;
import com.pte.itembank.dto.response.QuestionTypeResponse;

public final class QuestionTypeMapper {

    private QuestionTypeMapper() {
    }

    public static QuestionTypeResponse toResponse(QuestionTypeDefinition definition) {
        return toResponse(definition, TaskRuntimeProfileRegistry.descriptorFor(definition.getCode()));
    }

    public static QuestionTypeResponse toResponse(QuestionTypeDefinition definition,
            TaskRuntimeProfileDescriptor runtimeProfile) {
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
                definition.isUsesOptionOrderAsCorrectPosition(), runtimeProfile, definition.getCode());
    }
}
