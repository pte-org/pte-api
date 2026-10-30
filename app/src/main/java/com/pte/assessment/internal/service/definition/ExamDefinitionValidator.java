package com.pte.assessment.internal.service.definition;

import com.pte.assessment.internal.service.TaskTypeScoringMethods;
import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.ExamDefinitionItem;
import com.pte.assessment.domain.enums.TemplatePolicy;
import com.pte.itembank.ItembankService;
import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.TaskRuntimeProfileService;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.assessment.internal.exception.ExamDefinitionValidationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates exam definition templates for catalog integrity, runtime profile compatibility,
 * and sufficient question bank inventory before approval/activation.
 */
class ExamDefinitionValidator {

    private final QuestionTypeService questionTypeService;
    private final TaskRuntimeProfileService runtimeProfileService;
    private final ItembankService itembankService;

    ExamDefinitionValidator(QuestionTypeService questionTypeService, TaskRuntimeProfileService runtimeProfileService,
            ItembankService itembankService) {
        this.questionTypeService = questionTypeService;
        this.runtimeProfileService = runtimeProfileService;
        this.itembankService = itembankService;
    }

    /**
     * Validates that all template items reference active task types with compatible runtime profiles.
     * Lenient mode allows incomplete profiles for CUSTOM drafts during editing; strict mode requires full readiness.
     */
    void validateCatalog(ExamDefinition template, boolean strict) {
        if (questionTypeService == null) {
            return;
        }
        boolean customPolicy = template.getTemplatePolicy() == TemplatePolicy.CUSTOM;
        List<String> errors = new ArrayList<>();
        List<ExamDefinitionItem> profileItems = new ArrayList<>();
        List<String> missingProfileCodes = new ArrayList<>();

        for (ExamDefinitionItem item : template.getItems()) {
            String logicalKey;
            try {
                logicalKey = logicalTaskKey(item);
                if (logicalKey == null || logicalKey.isBlank()) {
                    throw new IllegalArgumentException(ExamDefinitionConstants.MISSING_TASK_TYPE_KEY);
                }
                logicalKey = TaskTypeCodeCompatibility.normalizeTaskTypeKey(logicalKey);
                item.setTaskTypeKey(logicalKey);
                if (TaskTypeCodeCompatibility.isStandard(logicalKey)) {
                    item.setTaskType(logicalKey);
                } else {
                    item.setTaskType(null);
                }
            } catch (RuntimeException ex) {
                errors.add(ExamDefinitionConstants.TEMPLATE_TASK_TYPE_INVALID + logicalTaskKey(item));
                continue;
            }
            if (!questionTypeService.isActive(logicalKey)) {
                errors.add(ExamDefinitionConstants.TEMPLATE_TASK_TYPE_INVALID + logicalKey);
                continue;
            }
            String expectedSection = questionTypeService.findSectionByTaskTypeKey(logicalKey)
                    .orElse(standardSection(logicalKey));
            if (expectedSection == null || !expectedSection.equals(item.getSection())) {
                errors.add(ExamDefinitionConstants.TEMPLATE_SECTION_INVALID + logicalKey);
                continue;
            }
            if (!customPolicy && !TaskTypeCodeCompatibility.isStandard(logicalKey)) {
                errors.add(ExamDefinitionConstants.TEMPLATE_TASK_TYPE_INVALID + logicalKey);
                continue;
            }
            profileItems.add(item);
            if (item.pinnedRuntimeProfile() == null) {
                missingProfileCodes.add(logicalKey);
            }
        }

        Map<String, TaskRuntimeProfileDescriptor> activeProfiles = new LinkedHashMap<>();
        boolean activeProfileLookupFailed = false;
        if (!missingProfileCodes.isEmpty()) {
            try {
                for (String code : missingProfileCodes) {
                    activeProfiles.put(code, resolveActiveProfile(code));
                }
            } catch (RuntimeException ex) {
                activeProfileLookupFailed = true;
            }
        }

        for (ExamDefinitionItem item : profileItems) {
            if (item.pinnedRuntimeProfile() == null) {
                TaskRuntimeProfileDescriptor active = activeProfiles.get(logicalTaskKey(item));
                if (!activeProfileLookupFailed && active != null) {
                    item.pinRuntimeProfile(active);
                }
            }
        }

        List<TaskRuntimeProfileDescriptor> pinnedProfiles = profileItems.stream()
                .map(ExamDefinitionItem::pinnedRuntimeProfile)
                .filter(java.util.Objects::nonNull)
                .toList();
        Set<TaskRuntimeProfileDescriptor> invalidProfiles = profileItems.stream()
                .filter(item -> !customPolicy || TaskTypeCodeCompatibility.isStandard(logicalTaskKey(item)))
                .map(ExamDefinitionItem::pinnedRuntimeProfile)
                .filter(java.util.Objects::nonNull)
                .filter(profile -> !isAllowlistedProfile(profile))
                .collect(java.util.stream.Collectors.toSet());
        for (ExamDefinitionItem item : profileItems) {
            if (item.pinnedRuntimeProfile() == null || invalidProfiles.contains(item.pinnedRuntimeProfile())) {
                errors.add(ExamDefinitionConstants.TEMPLATE_PROFILE_INVALID + logicalTaskKey(item));
                continue;
            }
            try {
                if (item.getScoringMethod() != TaskTypeScoringMethods.resolve(item.pinnedRuntimeProfile())) {
                    errors.add(ExamDefinitionConstants.TEMPLATE_SCORING_PROFILE_INVALID + logicalTaskKey(item));
                }
            } catch (RuntimeException ex) {
                errors.add(ExamDefinitionConstants.TEMPLATE_SCORING_PROFILE_INVALID + logicalTaskKey(item));
            }
        }
        if (strict && !errors.isEmpty()) {
            throw new ExamDefinitionValidationException(String.join("; ", errors));
        }
    }

    /**
     * Validates that the question bank contains sufficient items for each task type
     * required by the template at the specified quantity.
     */
    void validateQuestionBank(ExamDefinition template) {
        if (itembankService == null) {
            return;
        }
        Set<String> taskTypeKeys = template.getItems().stream()
                .map(this::logicalTaskKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<String, Long> available = itembankService.countPublishedByTaskTypeKeys(taskTypeKeys);
        List<String> shortages = template.getItems().stream()
                .filter(item -> available.getOrDefault(logicalTaskKey(item), 0L) < item.getMaxCount())
                .map(item -> ExamDefinitionConstants.TEMPLATE_QUESTION_BANK_INSUFFICIENT
                        + logicalTaskKey(item) + " (required " + item.getMaxCount() + ", available "
                        + available.getOrDefault(logicalTaskKey(item), 0L) + ")")
                .toList();
        if (!shortages.isEmpty()) {
            throw new ExamDefinitionValidationException(String.join("; ", shortages));
        }
    }

    private boolean isAllowlistedProfile(TaskRuntimeProfileDescriptor profile) {
        return TaskRuntimeProfileRegistry.isAllowlistedContract(profile);
    }

    private String standardSection(String logicalKey) {
        try {
            return TaskTypeCodeCompatibility.isStandard(logicalKey)
                    ? com.pte.itembank.domain.enums.PteTaskType.valueOf(logicalKey).getSection().name()
                    : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    TaskRuntimeProfileDescriptor resolveActiveProfile(String taskTypeCode) {
        if (TaskTypeCodeCompatibility.isStandard(taskTypeCode)) {
            return runtimeProfileService == null
                    ? TaskRuntimeProfileRegistry.descriptorFor(taskTypeCode)
                    : runtimeProfileService.resolveActive(taskTypeCode);
        }
        if (questionTypeService == null) {
            throw new ExamDefinitionValidationException(ExamDefinitionConstants.TEMPLATE_TASK_TYPE_INVALID + taskTypeCode);
        }
        var contract = questionTypeService.resolveRuntimeContract(taskTypeCode);
        return new TaskRuntimeProfileDescriptor(taskTypeCode, contract.profileKey(), contract.profileVersion(),
                contract.behaviorKey(), contract.rendererKey(), contract.answerSchemaVersion(),
                contract.scoringProfileKey(), contract.scoringProfileVersion(),
                contract.requiredClientCapabilities(), contract.status(), contract.screenKey(),
                contract.contractVersion(), contract.scoringMode(), contract.minSupportedAppVersion(),
                contract.authoringContractKey(), contract.authoringContractVersion());
    }

    private String logicalTaskKey(ExamDefinitionItem item) {
        return item.getTaskTypeKey() == null ? TaskTypeCodeCompatibility.normalizeForLookup(item.getTaskType())
                : TaskTypeCodeCompatibility.normalizeTaskTypeKey(item.getTaskTypeKey());
    }
}
