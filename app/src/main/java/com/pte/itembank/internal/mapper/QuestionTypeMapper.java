package com.pte.itembank.internal.mapper;

import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.TaskRuntimeContractDescriptor;
import com.pte.itembank.domain.QuestionTypeDefinition;
import com.pte.itembank.dto.response.QuestionTypeResponse;
import com.pte.itembank.dto.response.TaskTypeEditabilityResponse;
import com.pte.itembank.dto.response.TaskTypeReadinessResponse;

import java.util.List;

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
                definition.isUsesOptionOrderAsCorrectPosition(), runtimeProfile, definition.getCode(),
                definition.getTaskTypeKey() == null ? definition.getCode() : definition.getTaskTypeKey(),
                definition.getScreenKey() == null
                        ? runtimeProfile == null ? null : runtimeProfile.screenKey()
                        : definition.getScreenKey(),
                definition.getRuntimeProfileVersion() == null
                        ? runtimeProfile == null ? null : runtimeProfile.contractVersion()
                        : definition.getRuntimeProfileVersion(),
                readiness(definition, runtimeProfile), editability(definition));
    }

    public static QuestionTypeResponse toResponse(QuestionTypeDefinition definition,
            TaskRuntimeContractDescriptor contract, boolean locked) {
        TaskRuntimeProfileDescriptor legacy = contract == null ? null : new TaskRuntimeProfileDescriptor(
                definition.getTaskTypeKey(), contract.profileKey(), contract.profileVersion(),
                contract.behaviorKey(), contract.rendererKey(), contract.answerSchemaVersion(),
                contract.scoringProfileKey(), contract.scoringProfileVersion(),
                contract.requiredClientCapabilities(), contract.status(), contract.screenKey(),
                contract.contractVersion(), contract.scoringMode(), contract.minSupportedAppVersion(),
                contract.authoringContractKey(), contract.authoringContractVersion());
        QuestionTypeResponse response = toResponse(definition, legacy);
        return new QuestionTypeResponse(response.publicId(), response.code(), response.displayName(),
                response.shortName(), response.section(), response.scored(), response.active(),
                response.displayOrder(), response.requiresAudioPrompt(), response.requiresImagePrompt(),
                response.requiresPromptText(), response.requiresOptions(), response.requiresCorrectAnswer(),
                response.requiresWordCount(), response.requiresSingleCorrectOption(),
                response.usesOptionOrderAsCorrectPosition(), legacy, response.taskTypeCode(),
                response.taskTypeKey(), response.screenKey(), response.contractVersion(),
                new TaskTypeReadinessResponse(contract != null && contract.active(),
                        contract == null ? "UNAVAILABLE" : "MANIFEST_REQUIRED", List.of()),
                new TaskTypeEditabilityResponse(false, !locked, true,
                        locked ? "This task type is used by a published template, so its runtime contract is locked." : null));
    }

    private static TaskTypeReadinessResponse readiness(QuestionTypeDefinition definition,
            TaskRuntimeProfileDescriptor runtime) {
        return new TaskTypeReadinessResponse(runtime != null && runtime.active(),
                runtime == null ? "UNAVAILABLE" : "MANIFEST_REQUIRED", List.of());
    }

    private static TaskTypeEditabilityResponse editability(QuestionTypeDefinition definition) {
        boolean locked = definition.getRuntimeLockedAt() != null;
        return new TaskTypeEditabilityResponse(false, !locked, true,
                locked ? "This task type is used by a published template, so its runtime contract is locked." : null);
    }
}
