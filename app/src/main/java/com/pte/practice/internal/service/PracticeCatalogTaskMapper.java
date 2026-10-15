package com.pte.practice.internal.service;

import com.pte.itembank.PracticeQuestionService;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.dto.response.QuestionTypeResponse;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.dto.response.PracticeCatalogAvailability;
import com.pte.practice.internal.dto.response.PracticeCatalogTaskResponse;

import java.util.List;
import java.util.Locale;

/** Projects canonical task definitions into safe practice catalog rows. */
final class PracticeCatalogTaskMapper {

    private final PracticeQuestionService practiceQuestionService;

    PracticeCatalogTaskMapper(PracticeQuestionService practiceQuestionService) {
        this.practiceQuestionService = practiceQuestionService;
    }

    PracticeCatalogTaskResponse mapDefinition(QuestionTypeResponse definition) {
        String code = normalizeCode(definition.taskTypeCode() == null ? definition.code() : definition.taskTypeCode());
        String section = safeSection(definition.section(), code);
        TaskRuntimeProfileDescriptor profile = definition.runtime();
        if (profile == null) {
            profile = resolveProfile(code);
        }
        boolean allowlisted = profile != null && TaskRuntimeProfileRegistry.isAllowlistedContract(profile);
        boolean serverReady = definition.readiness() == null || definition.readiness().serverReady();
        boolean runtimeSupported = isClientRuntimeSupported(profile);
        boolean runtimeActive = profile != null && profile.active();
        boolean contentReady = hasPracticeContent(code);
        boolean runnable = allowlisted && runtimeActive && serverReady && runtimeSupported && contentReady;
        PracticeCatalogAvailability availability = runnable
                ? PracticeCatalogAvailability.RUNNABLE
                : allowlisted ? PracticeCatalogAvailability.VISIBLE : PracticeCatalogAvailability.UNAVAILABLE;
        String reason = availability == PracticeCatalogAvailability.RUNNABLE ? null
                : !runtimeSupported || !allowlisted
                        ? PracticeConstants.PRACTICE_UNSUPPORTED_RUNTIME
                        : PracticeConstants.PRACTICE_CONTENT_NOT_READY;
        String contentStatus = contentStatus(definition, serverReady, contentReady);
        return toTask(code, definition.displayName(), section, definition.scored(), profile,
                availability, reason, contentStatus, PracticeConstants.PRACTICE_PERSISTED_CATALOG_PROVENANCE);
    }

    PracticeCatalogTaskResponse fallbackTask(PteTaskType taskType) {
        TaskRuntimeProfileDescriptor profile = TaskRuntimeProfileRegistry.descriptorFor(taskType.name());
        boolean contentReady = hasPracticeContent(taskType.name());
        boolean runtimeSupported = isClientRuntimeSupported(profile) && profile != null && profile.active();
        PracticeCatalogAvailability availability = runtimeSupported && contentReady
                ? PracticeCatalogAvailability.RUNNABLE : PracticeCatalogAvailability.VISIBLE;
        return toTask(taskType.name(), humanize(taskType.name()), taskType.getSection().name(), taskType.isScored(),
                profile, availability, availability == PracticeCatalogAvailability.RUNNABLE ? null
                        : PracticeConstants.PRACTICE_CONTENT_NOT_READY,
                contentReady ? PracticeConstants.PRACTICE_RUNTIME_CONTRACT_READY_STATUS
                        : PracticeConstants.PRACTICE_RUNTIME_ONLY_STATUS,
                PracticeConstants.PRACTICE_RUNTIME_REGISTRY_PROVENANCE);
    }

    private String contentStatus(QuestionTypeResponse definition, boolean serverReady, boolean contentReady) {
        if (!contentReady) {
            return PracticeConstants.PRACTICE_RUNTIME_ONLY_STATUS;
        }
        if (definition.readiness() == null) {
            return PracticeConstants.PRACTICE_RUNTIME_CONTRACT_READY_STATUS;
        }
        return serverReady ? PracticeConstants.PRACTICE_SERVER_READY_STATUS
                : PracticeConstants.PRACTICE_SERVER_NOT_READY_STATUS;
    }

    private boolean hasPracticeContent(String taskTypeKey) {
        return practiceQuestionService == null || practiceQuestionService.hasPublishedQuestion(taskTypeKey);
    }

    private PracticeCatalogTaskResponse toTask(String code, String displayName, String section, boolean scored,
            TaskRuntimeProfileDescriptor profile, PracticeCatalogAvailability availability, String reason,
            String contentStatus, String provenance) {
        return new PracticeCatalogTaskResponse(
                code,
                displayName == null || displayName.isBlank() ? humanize(code) : displayName,
                section,
                scored,
                availability,
                reason,
                profile == null ? null : profile.profileKey(),
                profile == null ? null : profile.profileVersion(),
                profile == null ? null : profile.rendererKey(),
                profile == null ? null : profile.contractVersion(),
                profile == null ? null : profile.answerSchemaVersion(),
                profile == null ? List.of() : profile.requiredClientCapabilities(),
                contentStatus,
                provenance);
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return PracticeConstants.PRACTICE_UNKNOWN_TASK_CODE;
        }
        try {
            return TaskTypeCodeCompatibility.normalizeTaskTypeKey(code);
        } catch (RuntimeException ex) {
            return code.trim().toUpperCase(Locale.ROOT);
        }
    }

    private String safeSection(String rawSection, String taskCode) {
        if (rawSection != null && !rawSection.isBlank()) {
            return rawSection.trim().toUpperCase(Locale.ROOT);
        }
        try {
            return PteTaskType.valueOf(taskCode).getSection().name();
        } catch (RuntimeException ex) {
            return PracticeConstants.PRACTICE_UNKNOWN_TASK_CODE;
        }
    }

    private TaskRuntimeProfileDescriptor resolveProfile(String code) {
        try {
            return TaskRuntimeProfileRegistry.descriptorFor(code);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean isClientRuntimeSupported(TaskRuntimeProfileDescriptor profile) {
        return profile != null
                && PracticeConstants.PRACTICE_CLIENT_SUPPORTED_RENDERER_KEYS.contains(profile.rendererKey());
    }

    private String humanize(String value) {
        if (value == null || value.isBlank()) {
            return PracticeConstants.PRACTICE_DEFAULT_DISPLAY_NAME;
        }
        String[] words = value.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
