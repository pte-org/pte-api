package com.pte.attempt.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.attempt.internal.config.TaskTimingConfig;
import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.attempt.internal.exception.ExamCapabilityException;
import com.pte.attempt.internal.exception.MissingAudioDurationException;
import com.pte.attempt.internal.exception.MissingAudioPromptException;
import com.pte.attempt.internal.exception.MissingImagePromptException;
import com.pte.attempt.internal.exception.TaskTimingNotConfiguredException;
import com.pte.attempt.internal.service.cache.ExamItemView;
import com.pte.itembank.TaskRuntimeContractConstants;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.media.MediaService;
import com.pte.media.dto.response.PresignedDownloadResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the attempt runtime view of a frozen exam: content from {@code assessment}, timing from the exam
 * definition the version was built from (falling back to the task timing config for task types the definition
 * does not list), and presigned media URLs from {@code media}. Every item of the exam is returned, in order.
 * The result is cached per session by {@link ExamContentService}, so this runs once per exam, not per request.
 */
@Component
class ExamItemLoader {

    private static final String LISTENING_SECTION = "LISTENING";
    private static final String DESCRIBE_IMAGE_TASK_TYPE = "DESCRIBE_IMAGE";

    private final AssessmentService assessmentService;
    private final MediaService mediaService;
    private final TaskTimingConfig taskTimingConfig;
    private final ExamDefinitionService examDefinitionService;

    ExamItemLoader(AssessmentService assessmentService, MediaService mediaService,
            TaskTimingConfig taskTimingConfig, ExamDefinitionService examDefinitionService) {
        this.assessmentService = assessmentService;
        this.mediaService = mediaService;
        this.taskTimingConfig = taskTimingConfig;
        this.examDefinitionService = examDefinitionService;
    }

    /** The exam version's own header, for callers that need its definition and version before the items. */
    SnapshotContentResponse content(UUID examVersionPublicId) {
        return assessmentService.getFullContent(examVersionPublicId);
    }

    List<ExamItemView> load(SnapshotContentResponse content, long mediaUrlTtlSeconds, UUID tenantId) {
        ExamDefinitionResponse definition = examDefinitionService.getByPublicId(content.examDefinitionPublicId());
        Map<String, ExamDefinitionItemResponse> definitionItemsByTaskType = definition.items().stream()
                .collect(Collectors.toMap(item -> TaskTypeCodeCompatibility.normalizeTaskTypeKey(
                        item.taskTypeKey() == null ? item.taskType() : item.taskTypeKey()),
                        Function.identity(), (first, second) -> first));
        return content.items().stream()
                .sorted(Comparator.comparingInt(SnapshotContentResponse.Item::orderIndex))
                .map(item -> toView(item, definitionItemsByTaskType, mediaUrlTtlSeconds, tenantId))
                .toList();
    }

    /**
     * Timing source order per task type: the exam definition wins whenever it has a row for the task type (the 17
     * static task types read prep/response straight off it, and the 5 audio-prompt Speaking types read their
     * {@code preRecordSeconds} off its {@code prepSeconds}, still taking {@code preListenSeconds} from the timing
     * config). Only a task type absent from the definition falls back to the timing config entirely.
     */
    private ExamItemView toView(SnapshotContentResponse.Item source,
            Map<String, ExamDefinitionItemResponse> definitionItemsByTaskType, long mediaUrlTtlSeconds,
            UUID tenantId) {
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
            // LISTENING's audio is mandatory; a missing one is an authoring data problem and must fail loudly.
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
                // Audio-prompt types are always scored, so a valid definition always lists them.
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
        return new ExamItemView(itemPublicId(source), source.orderIndex(), source.section(), source.taskType(),
                source.title(), source.promptText(), source.audioPromptRef(), source.imagePromptRef(),
                source.referenceAnswerText(), source.correctAnswerText(), source.minWordCount(),
                source.maxWordCount(), source.optionsJson(), prepSeconds, responseSeconds, preListenSeconds,
                preRecordSeconds, image == null ? null : image.url(), audio == null ? null : audio.url(),
                expiry(audio), taskTypeCode, runtime, mappingVersion, mappingStatus,
                source.taskTypeDisplayName() == null ? taskTypeCode : source.taskTypeDisplayName());
    }

    private UUID itemPublicId(SnapshotContentResponse.Item source) {
        return source.itemPublicId();
    }

    private Instant expiry(PresignedDownloadResponse presigned) {
        return presigned != null && presigned.expiresInSeconds() > 0
                ? Instant.now().plusSeconds(presigned.expiresInSeconds())
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

    /**
     * The exam item is the only runtime-provenance source: a canonical descriptor must be allowlisted (standard
     * types) or complete (custom types); a missing descriptor is only valid for the named legacy mapping.
     */
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
