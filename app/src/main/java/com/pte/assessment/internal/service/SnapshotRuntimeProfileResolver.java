package com.pte.assessment.internal.service;

import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.internal.exception.SnapshotRuntimeContractException;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.TaskRuntimeProfileService;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.dto.response.QuestionFreezeView;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves and validates runtime profiles for snapshot items, ensuring compatibility
 * with the active exam template and adherence to allowlisting policies for standard tasks.
 */
class SnapshotRuntimeProfileResolver {

    private final TaskRuntimeProfileService runtimeProfileService;

    SnapshotRuntimeProfileResolver(TaskRuntimeProfileService runtimeProfileService) {
        this.runtimeProfileService = runtimeProfileService;
    }

    /**
     * Resolves the runtime profile for a snapshot item's task type.
     * Attempts to find profile from the template first; falls back to legacy task type or registry.
     * Throws exception if profile is missing or incompatible.
     */
    TaskRuntimeProfileDescriptor resolveRuntimeProfile(String taskTypeKey, PteTaskType legacyTaskType,
            Map<String, ExamDefinitionItemResponse> templateItemsByTaskType) {
        ExamDefinitionItemResponse templateItem = templateItemsByTaskType.get(taskTypeKey);
        TaskRuntimeProfileDescriptor runtime = templateItem == null && legacyTaskType != null && !legacyTaskType.isScored()
                ? TaskRuntimeProfileRegistry.descriptorFor(legacyTaskType.name())
                : templateItem == null && runtimeProfileService == null
                        && legacyTaskType != null ? TaskRuntimeProfileRegistry.descriptorFor(legacyTaskType.name())
                        : templateItem == null ? null : templateItem.runtime();
        if (runtime == null) {
            throw new SnapshotRuntimeContractException(
                    "Missing runtime profile for task type " + taskTypeKey + " in the active score template");
        }
        if (!taskTypeKey.equals(runtime.taskTypeCode())) {
            throw new SnapshotRuntimeContractException(
                    "Runtime profile task type " + runtime.taskTypeCode() + " does not match " + taskTypeKey);
        }
        if (TaskTypeCodeCompatibility.isStandard(taskTypeKey)
                && !TaskRuntimeProfileRegistry.isAllowlistedContract(runtime)) {
            throw new SnapshotRuntimeContractException(
                    "Runtime profile contract differs from the allowlisted profile for " + taskTypeKey);
        }
        if (!TaskTypeCodeCompatibility.isStandard(taskTypeKey)
                && (!("ACTIVE".equals(runtime.status()) || "RETIRED".equals(runtime.status()))
                || runtime.screenKey() == null || runtime.contractVersion() < 1
                || runtime.answerSchemaVersion() < 1 || runtime.scoringProfileKey() == null
                || runtime.scoringMode() == null)) {
            throw new SnapshotRuntimeContractException(
                    "Custom runtime profile is incomplete for " + taskTypeKey);
        }
        return runtime;
    }

    /**
     * Validates that all runtime profiles in the frozen question set are allowlisted
     * for standard tasks and properly persisted for custom tasks.
     */
    void validateRuntimeProfiles(List<QuestionFreezeView> frozenQuestions,
            Map<String, ExamDefinitionItemResponse> templateItemsByTaskType) {
        if (runtimeProfileService == null) {
            return;
        }
        List<TaskRuntimeProfileDescriptor> profiles = frozenQuestions.stream()
                .map(question -> resolveRuntimeProfile(
                        question.taskTypeKey() == null && question.pteTaskType() != null
                                ? question.pteTaskType().name() : question.taskTypeKey(),
                        question.pteTaskType(), templateItemsByTaskType))
                .distinct()
                .toList();
        Set<TaskRuntimeProfileDescriptor> invalidStandardProfiles = profiles.stream()
                .filter(profile -> TaskTypeCodeCompatibility.isStandard(profile.taskTypeCode()))
                .filter(profile -> !TaskRuntimeProfileRegistry.isAllowlistedContract(profile))
                .collect(Collectors.toSet());
        if (!invalidStandardProfiles.isEmpty()) {
            throw new SnapshotRuntimeContractException(
                    "One or more runtime profiles are not allowlisted or persisted consistently");
        }
    }
}
