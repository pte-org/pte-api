package com.pte.assessment.dto.response;

import com.pte.itembank.TaskRuntimeProfileDescriptor;

import java.util.List;
import java.util.UUID;

/**
 * FULL-fidelity snapshot content (prompts, options incl. correct flags,
 * reference/correct answers) — trusted application-call surface only
 * ({@link com.pte.assessment.AssessmentService#getFullContent}). {@code attempt}
 * pins this at attempt-create. Never expose this on a human-facing endpoint;
 * {@link SnapshotResponse} is the answer-stripped summary students/hosts can reach.
 */
public record SnapshotContentResponse(
        UUID publicId,
        String name,
        int version,
        UUID scoreTemplatePublicId,
        Integer scoreTemplateVersion,
        UUID tenantId,
        List<Item> items) {

    /** Source-compatible constructor for callers created before template-version pinning. */
    public SnapshotContentResponse(UUID publicId, String name, int version, UUID scoreTemplatePublicId,
            UUID tenantId, List<Item> items) {
        this(publicId, name, version, scoreTemplatePublicId, null, tenantId, items);
    }

    public record Item(
            int orderIndex,
            String section,
            String taskType,
            String title,
            String promptText,
            UUID audioPromptRef,
            UUID imagePromptRef,
            String referenceAnswerText,
            String correctAnswerText,
            Integer minWordCount,
            Integer maxWordCount,
            String optionsJson,
            String taskTypeKey,
            String taskTypeDisplayName,
            String taskTypeCode,
            TaskRuntimeProfileDescriptor runtime,
            String runtimeMappingVersion,
            String runtimeMappingStatus) {

        public Item(int orderIndex, String section, String taskType, String title, String promptText,
                UUID audioPromptRef, UUID imagePromptRef, String referenceAnswerText,
                    String correctAnswerText, Integer minWordCount, Integer maxWordCount, String optionsJson,
                String taskTypeCode, TaskRuntimeProfileDescriptor runtime) {
            this(orderIndex, section, taskType, title, promptText, audioPromptRef, imagePromptRef,
                    referenceAnswerText, correctAnswerText, minWordCount, maxWordCount, optionsJson,
                    taskTypeCode, title, taskTypeCode, runtime, null, null);
        }

        public Item(int orderIndex, String section, String taskType, String title, String promptText,
                UUID audioPromptRef, UUID imagePromptRef, String referenceAnswerText,
                String correctAnswerText, Integer minWordCount, Integer maxWordCount, String optionsJson,
                String taskTypeCode, TaskRuntimeProfileDescriptor runtime,
                String runtimeMappingVersion, String runtimeMappingStatus) {
            this(orderIndex, section, taskType, title, promptText, audioPromptRef, imagePromptRef,
                    referenceAnswerText, correctAnswerText, minWordCount, maxWordCount, optionsJson,
                    taskTypeCode, title, taskTypeCode, runtime, runtimeMappingVersion, runtimeMappingStatus);
        }

        public Item(int orderIndex, String section, String taskType, String title, String promptText,
                    UUID audioPromptRef, UUID imagePromptRef, String referenceAnswerText,
                String correctAnswerText, Integer minWordCount, Integer maxWordCount, String optionsJson) {
            this(orderIndex, section, taskType, title, promptText, audioPromptRef, imagePromptRef,
                    referenceAnswerText, correctAnswerText, minWordCount, maxWordCount, optionsJson,
                    taskType, title, taskType, null, null, null);
        }

        public Item(int orderIndex, String section, String taskType, String title,
                String promptText, UUID audioPromptRef, UUID imagePromptRef,
                String referenceAnswerText, String correctAnswerText, Integer minWordCount,
                Integer maxWordCount, String optionsJson, String taskTypeKey,
                String taskTypeDisplayName, String taskTypeCode, TaskRuntimeProfileDescriptor runtime,
                String runtimeMappingVersion, String runtimeMappingStatus) {
            this.orderIndex = orderIndex;
            this.section = section;
            this.taskType = taskType;
            this.title = title;
            this.promptText = promptText;
            this.audioPromptRef = audioPromptRef;
            this.imagePromptRef = imagePromptRef;
            this.referenceAnswerText = referenceAnswerText;
            this.correctAnswerText = correctAnswerText;
            this.minWordCount = minWordCount;
            this.maxWordCount = maxWordCount;
            this.optionsJson = optionsJson;
            this.taskTypeKey = taskTypeKey;
            this.taskTypeDisplayName = taskTypeDisplayName;
            this.taskTypeCode = taskTypeCode;
            this.runtime = runtime;
            this.runtimeMappingVersion = runtimeMappingVersion;
            this.runtimeMappingStatus = runtimeMappingStatus;
        }
    }
}
