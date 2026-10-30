package com.pte.assessment.internal.service;

import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.dto.response.ResolvedExamItemResponse;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.assessment.internal.config.TaskTimingConfig;
import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.attempt.internal.exception.ExamCapabilityException;
import com.pte.attempt.internal.exception.content.MissingAudioDurationException;
import com.pte.attempt.internal.exception.content.MissingAudioPromptException;
import com.pte.attempt.internal.exception.content.MissingImagePromptException;
import com.pte.assessment.internal.exception.content.TaskTimingNotConfiguredException;
import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.TaskRuntimeContractConstants;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.media.MediaService;
import com.pte.media.dto.response.PresignedDownloadResponse;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves the immutable snapshot content and its pinned delivery contract.
 * Both attempt delivery and host preview call this seam so timing/runtime
 * behavior cannot drift between the two experiences.
 */
@Component
public class ExamItemRuntimeResolver {

    private static final String LISTENING_SECTION = "LISTENING";
    private static final String DESCRIBE_IMAGE_TASK_TYPE = "DESCRIBE_IMAGE";

    private final MediaService mediaService;
    private final TaskTimingConfig taskTimingConfig;
    private final ExamDefinitionService examDefinitionService;
    private final QuestionTypeService questionTypeService;
    private final Clock clock;

    public ExamItemRuntimeResolver(MediaService mediaService, TaskTimingConfig taskTimingConfig,
            ExamDefinitionService examDefinitionService, QuestionTypeService questionTypeService, Clock clock) {
        this.mediaService = mediaService;
        this.taskTimingConfig = taskTimingConfig;
        this.examDefinitionService = examDefinitionService;
        this.questionTypeService = questionTypeService;
        this.clock = clock;
    }

    public List<ResolvedExamItemResponse> resolve(SnapshotContentResponse content, long mediaUrlTtlSeconds,
            UUID tenantId) {
        ExamDefinitionResponse definition = examDefinitionService.getByPublicId(content.examDefinitionPublicId());
        Map<String, ExamDefinitionItemResponse> definitionItemsByTaskType = definition.items().stream()
                .collect(Collectors.toMap(item -> TaskTypeCodeCompatibility.normalizeTaskTypeKey(
                        item.taskTypeKey() == null ? item.taskType() : item.taskTypeKey()),
                        Function.identity(), (first, second) -> first));
        List<SnapshotContentResponse.Item> items = content.items().stream()
                .sorted(Comparator.comparingInt(SnapshotContentResponse.Item::orderIndex))
                .toList();
        // Instruction is display text read live, not frozen in the snapshot, so admin edits reach published exams.
        Map<String, String> instructionsByTaskType = questionTypeService.findInstructionsByTaskTypeKeys(
                items.stream().map(this::requireCompatibleTaskType).collect(Collectors.toSet()));
        return items.stream()
                .map(item -> resolveItem(item, definitionItemsByTaskType, instructionsByTaskType,
                        mediaUrlTtlSeconds, tenantId))
                .toList();
    }

    private ResolvedExamItemResponse resolveItem(SnapshotContentResponse.Item source,
            Map<String, ExamDefinitionItemResponse> definitionItemsByTaskType,
            Map<String, String> instructionsByTaskType, long mediaUrlTtlSeconds, UUID tenantId) {
        String taskTypeCode = requireCompatibleTaskType(source);
        ExamDefinitionItemResponse definitionItem = definitionItemsByTaskType.get(taskTypeCode);
        TaskTimingConfig.Timing jsonTiming = definitionItem != null
                ? taskTimingConfig.timingForIfConfigured(taskTypeCode)
                : taskTimingConfig.timingFor(taskTypeCode);
        boolean isAudioPromptType = jsonTiming != null && jsonTiming.preListenSeconds() != null;
        int responseSeconds = definitionItem != null ? definitionItem.responseSeconds() : jsonTiming.responseSeconds();

        TaskRuntimeProfileDescriptor runtime = requireRuntimeContract(source, taskTypeCode);
        String mappingVersion = runtime == null ? TaskRuntimeContractConstants.MAPPING_VERSION_LEGACY
                : TaskRuntimeContractConstants.MAPPING_VERSION_CANONICAL;
        String mappingStatus = runtime == null ? TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_LEGACY
                : TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_CANONICAL;

        PresignedDownloadResponse audio = null;
        if (LISTENING_SECTION.equals(source.section())) {
            if (source.audioPromptRef() == null) {
                throw new MissingAudioPromptException();
            }
            audio = mediaService.presignGet(source.audioPromptRef(), mediaUrlTtlSeconds, tenantId);
        } else if (source.audioPromptRef() != null) {
            audio = mediaService.presignGet(source.audioPromptRef(), mediaUrlTtlSeconds, tenantId);
        }
        PresignedDownloadResponse image = null;
        if (DESCRIBE_IMAGE_TASK_TYPE.equals(taskTypeCode) && source.imagePromptRef() == null) {
            throw new MissingImagePromptException();
        }
        if (source.imagePromptRef() != null) {
            image = mediaService.presignGet(source.imagePromptRef(), mediaUrlTtlSeconds, tenantId);
        }

        int prepSeconds;
        Integer preListenSeconds = null;
        Integer preRecordSeconds = null;
        if (isAudioPromptType) {
            if (audio == null || audio.durationSeconds() == null) {
                throw new MissingAudioDurationException();
            }
            if (definitionItem == null) {
                throw new TaskTimingNotConfiguredException();
            }
            preListenSeconds = jsonTiming.preListenSeconds();
            preRecordSeconds = definitionItem.prepSeconds();
            prepSeconds = preListenSeconds + audio.durationSeconds() + preRecordSeconds;
        } else if (definitionItem != null) {
            prepSeconds = definitionItem.prepSeconds();
        } else {
            prepSeconds = jsonTiming.prepSeconds();
        }

        return new ResolvedExamItemResponse(
                source.itemPublicId(),
                source.sourceQuestionPublicId(),
                source.orderIndex(),
                source.section(),
                source.taskType(),
                source.taskTypeKey(),
                taskTypeCode,
                source.taskTypeDisplayName() == null ? taskTypeCode : source.taskTypeDisplayName(),
                source.title(),
                source.promptText(),
                source.audioPromptRef(),
                source.imagePromptRef(),
                source.referenceAnswerText(),
                source.correctAnswerText(),
                source.minWordCount(),
                source.maxWordCount(),
                source.optionsJson(),
                prepSeconds,
                responseSeconds,
                preListenSeconds,
                preRecordSeconds,
                audio == null ? null : audio.durationSeconds(),
                audio == null ? null : audio.url(),
                expiry(audio),
                image == null ? null : image.url(),
                expiry(image),
                runtime,
                mappingVersion,
                mappingStatus,
                instructionsByTaskType.get(taskTypeCode));
    }

    private Instant expiry(PresignedDownloadResponse presigned) {
        return presigned != null && presigned.expiresInSeconds() > 0
                ? clock.instant().plusSeconds(presigned.expiresInSeconds())
                : null;
    }

    private String requireCompatibleTaskType(SnapshotContentResponse.Item source) {
        String taskTypeCode = TaskTypeCodeCompatibility.normalizeTaskTypeKey(
                source.taskTypeKey() == null
                        ? source.taskTypeCode() == null ? source.taskType() : source.taskTypeCode()
                        : source.taskTypeKey());
        try {
            try {
                if (!TaskTypeCodeCompatibility.parse(taskTypeCode).getSection().name().equals(source.section())) {
                    throw new IllegalArgumentException(AttemptConstants.SNAPSHOT_TASK_TYPE_SECTION_MISMATCH);
                }
            } catch (RuntimeException customKey) {
                if (source.section() == null || source.section().isBlank()) {
                    throw new IllegalArgumentException(AttemptConstants.SNAPSHOT_CUSTOM_TASK_TYPE_SECTION_REQUIRED);
                }
            }
        } catch (RuntimeException ex) {
            throw new ExamCapabilityException(AttemptConstants.EXAM_CONFIGURATION_NOT_COMPATIBLE, List.of());
        }
        return taskTypeCode;
    }

    private TaskRuntimeProfileDescriptor requireRuntimeContract(SnapshotContentResponse.Item source,
            String taskTypeCode) {
        TaskRuntimeProfileDescriptor runtime = source.runtime();
        if (runtime == null) {
            boolean unknownStatus = source.runtimeMappingStatus() != null
                    && !TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_LEGACY
                            .equals(source.runtimeMappingStatus());
            boolean unknownVersion = source.runtimeMappingVersion() != null
                    && !TaskRuntimeContractConstants.MAPPING_VERSION_LEGACY.equals(source.runtimeMappingVersion());
            if (unknownStatus || unknownVersion) {
                throw new ExamCapabilityException(AttemptConstants.EXAM_CONFIGURATION_NOT_COMPATIBLE, List.of());
            }
            return null;
        }
        if (!TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_CANONICAL.equals(source.runtimeMappingStatus())
                || !TaskRuntimeContractConstants.MAPPING_VERSION_CANONICAL.equals(source.runtimeMappingVersion())) {
            throw new ExamCapabilityException(AttemptConstants.EXAM_CONFIGURATION_NOT_COMPATIBLE, List.of());
        }
        try {
            if (!taskTypeCode.equals(runtime.taskTypeCode())) {
                throw new IllegalArgumentException(AttemptConstants.SNAPSHOT_RUNTIME_PROFILE_NOT_ALLOWLISTED);
            }
            if (TaskTypeCodeCompatibility.isStandard(taskTypeCode)) {
                if (!TaskRuntimeProfileRegistry.isAllowlistedContract(runtime)) {
                    throw new IllegalArgumentException(AttemptConstants.SNAPSHOT_STANDARD_RUNTIME_PROFILE_MISMATCH);
                }
            } else if (!("ACTIVE".equals(runtime.status()) || "RETIRED".equals(runtime.status()))
                    || runtime.screenKey() == null || runtime.contractVersion() < 1
                    || runtime.answerSchemaVersion() < 1 || runtime.scoringProfileKey() == null
                    || runtime.scoringMode() == null) {
                throw new IllegalArgumentException(AttemptConstants.SNAPSHOT_CUSTOM_RUNTIME_PROFILE_INCOMPLETE);
            }
        } catch (RuntimeException ex) {
            throw new ExamCapabilityException(AttemptConstants.EXAM_CONFIGURATION_NOT_COMPATIBLE, List.of());
        }
        return runtime;
    }
}
