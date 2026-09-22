package com.pte.attempt.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.PinnedExamSnapshot;
import com.pte.attempt.domain.PinnedItem;
import com.pte.attempt.internal.config.TaskTimingConfig;
import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.attempt.internal.exception.ExamCapabilityException;
import com.pte.attempt.internal.exception.MissingAudioDurationException;
import com.pte.attempt.internal.exception.MissingAudioPromptException;
import com.pte.attempt.internal.exception.MissingImagePromptException;
import com.pte.attempt.internal.exception.TaskTimingNotConfiguredException;
import com.pte.media.MediaService;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeContractConstants;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.session.SessionService;
import com.pte.session.dto.response.EntitlementResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Orchestrates the ONE guarded attempt-create call (Phase 07 design
 * constraint): {@code session} for entitlement, {@code assessment} for full
 * snapshot content, {@code media} for presigned audio/image URLs — each
 * exactly once, in-process — then deep-copies the result into a
 * self-contained {@link PinnedExamSnapshot}. After this returns, attempt
 * never calls another module again for this attempt.
 *
 * <p>Every item of the source snapshot gets pinned, in its original order
 * (Plan B removed {@code SessionComposition} — there is no host-chosen
 * subset or per-type timing override anymore; the skill selection already
 * happened at exam-generation time, in {@code assessment}).
 */
@Service
public class SnapshotPinService {

    private static final String LISTENING_SECTION = "LISTENING";
    private static final String DESCRIBE_IMAGE_TASK_TYPE = "DESCRIBE_IMAGE";
    private static final long AUDIO_URL_GRACE_SECONDS = 60;

    private final SessionService sessionService;
    private final AssessmentService assessmentService;
    private final MediaService mediaService;
    private final TaskTimingConfig taskTimingConfig;
    private final ScoreTemplateService scoreTemplateService;

    public SnapshotPinService(SessionService sessionService, AssessmentService assessmentService,
                              MediaService mediaService, TaskTimingConfig taskTimingConfig,
                              ScoreTemplateService scoreTemplateService) {
        this.sessionService = sessionService;
        this.assessmentService = assessmentService;
        this.mediaService = mediaService;
        this.taskTimingConfig = taskTimingConfig;
        this.scoreTemplateService = scoreTemplateService;
    }

    public PinnedExamSnapshot pin(ExamAttempt attempt, UUID sessionPublicId, UUID studentPublicId) {
        // Throws session's own NotEntitledException (403) unmodified if the
        // student isn't enrolled or the session isn't OPEN — no defensive
        // null-check needed, unlike the pre-split HTTP call this replaces:
        // an in-process call either returns a fully-populated response or
        // throws, it never returns null/malformed data.
        EntitlementResponse entitlement = sessionService.checkEntitlement(sessionPublicId, studentPublicId);
        // Throws assessment's own BlueprintNotFoundException (404) unmodified if missing.
        SnapshotContentResponse content = assessmentService.getFullContent(entitlement.snapshotPublicId());
        // Resolved once per pin, by the publicId the snapshot itself pinned at
        // publish time (spec FR-14) — NOT scoreTemplateService.getActive(),
        // so an attempt pinned today keeps reading the exact template its exam
        // was published under even after a later template is activated.
        ScoreTemplateResponse scoreTemplate = scoreTemplateService.getByPublicId(content.scoreTemplatePublicId());
        Map<String, ScoreTemplateItemResponse> templateItemsByTaskType = scoreTemplate.items().stream()
                .collect(Collectors.toMap(item -> TaskTypeCodeCompatibility.normalizeForLookup(item.taskType()),
                        Function.identity(), (a, b) -> a));

        PinnedExamSnapshot pinned = new PinnedExamSnapshot();
        pinned.setAttempt(attempt);
        pinned.setSourceSnapshotPublicId(content.publicId());
        pinned.setSourceSessionPublicId(sessionPublicId);
        pinned.setScoreTemplatePublicId(content.scoreTemplatePublicId());
        pinned.setScoreTemplateVersion(content.scoreTemplateVersion() == null
                ? scoreTemplate.version() : content.scoreTemplateVersion());
        pinned.setTenantId(entitlement.tenantId());
        pinned.setReplayPolicyType(entitlement.policy().replayPolicyType());
        pinned.setReplayPolicyLimit(entitlement.policy().replayPolicyLimit());
        pinned.setDeviceCheckRequired(Boolean.TRUE.equals(entitlement.policy().deviceCheckRequired()));
        pinned.setProctorRequired(Boolean.TRUE.equals(entitlement.policy().proctorRequired()));
        pinned.setAnswerIntegrityLevel(entitlement.policy().answerIntegrityLevel());
        pinned.setLockdownMode(entitlement.policy().lockdownMode());

        long audioUrlTtlSeconds = Duration.between(entitlement.opensAt(), entitlement.closesAt()).getSeconds()
                + AUDIO_URL_GRACE_SECONDS;

        content.items().stream()
                .sorted(Comparator.comparingInt(SnapshotContentResponse.Item::orderIndex))
                .forEach(item -> pinned.addItem(toPinnedItem(item, templateItemsByTaskType, audioUrlTtlSeconds,
                        entitlement.tenantId())));

        return pinned;
    }

    /**
     * Timing source order per task type (spec FR-14): the pinned {@link
     * ScoreTemplate} wins whenever it has a row for this {@code taskType}
     * ({@code templateItem != null}) — the 17 static task types read
     * prep/response straight off it, and the 5 audio-prompt Speaking types
     * read their {@code preRecordSeconds} off its {@code prepSeconds} column
     * (the seed convention documented in {@code V14__score_template.sql}),
     * while still reading {@code preListenSeconds} from {@code
     * taskTimingConfig} (no template column for it — a client sub-stage
     * split, not a scoring concern). Only a task type ABSENT from the
     * template ({@code PERSONAL_INTRODUCTION} today) falls back to {@code
     * taskTimingConfig.timingFor} entirely, exactly as before Phase 3.
     */
    private PinnedItem toPinnedItem(SnapshotContentResponse.Item source,
                                    Map<String, ScoreTemplateItemResponse> templateItemsByTaskType,
                                    long audioUrlTtlSeconds, UUID tenantId) {
        String taskTypeCode = source.taskTypeCode() == null
                ? TaskTypeCodeCompatibility.normalizeForLookup(source.taskType())
                : TaskTypeCodeCompatibility.normalizeForLookup(source.taskTypeCode());
        try {
            if (!taskTypeCode.equals(TaskTypeCodeCompatibility.normalizeForLookup(source.taskType()))) {
                throw new IllegalArgumentException("task type code does not match the frozen task type");
            }
            if (!TaskTypeCodeCompatibility.parse(taskTypeCode).getSection().name().equals(source.section())) {
                throw new IllegalArgumentException("task type section does not match the frozen snapshot section");
            }
        } catch (RuntimeException ex) {
            throw new ExamCapabilityException(AttemptConstants.EXAM_CONFIGURATION_NOT_COMPATIBLE, List.of());
        }
        ScoreTemplateItemResponse templateItem = templateItemsByTaskType.get(taskTypeCode);
        // Non-throwing when the template already has this taskType — most of
        // the 22 rows were deliberately REMOVED from task-timing.json (not just
        // individual fields), so the old throwing timingFor() would wrongly
        // fail every static template-known type (e.g. MC_READING_SINGLE).
        TaskTimingConfig.Timing jsonTiming = templateItem != null
                ? taskTimingConfig.timingForIfConfigured(taskTypeCode)
                : taskTimingConfig.timingFor(taskTypeCode);
        boolean isAudioPromptType = jsonTiming != null && jsonTiming.preListenSeconds() != null;

        // No composition override anymore (Plan B) — always the template/json value.
        int responseSeconds = templateItem != null ? templateItem.responseSeconds() : jsonTiming.responseSeconds();

        PinnedItem item = new PinnedItem();
        item.setOrderIndex(source.orderIndex());
        item.setSection(source.section());
        item.setTaskType(source.taskType());
        item.setTaskTypeCode(taskTypeCode);
        copyRuntimeContract(source, item, taskTypeCode);
        item.setTitle(source.title());
        item.setPromptText(source.promptText());
        item.setAudioPromptRef(source.audioPromptRef());
        item.setImagePromptRef(source.imagePromptRef());
        item.setReferenceAnswerText(source.referenceAnswerText());
        item.setCorrectAnswerText(source.correctAnswerText());
        item.setMinWordCount(source.minWordCount());
        item.setMaxWordCount(source.maxWordCount());
        item.setOptionsJson(source.optionsJson());
        item.setResponseSeconds(responseSeconds);
        // maxPlayCountOverride is never set anymore (Plan B removed SessionComposition) —
        // always null, meaning "inherit the pinned session-level replay policy".

        Integer audioDurationSeconds = null;
        if (LISTENING_SECTION.equals(source.section())) {
            // LISTENING's audioPromptRef is mandatory — a listening item with none is an
            // authoring data problem, not a client error, and must keep failing loudly.
            if (source.audioPromptRef() == null) {
                throw new MissingAudioPromptException();
            }
            audioDurationSeconds = resolveAudioUrl(item, source.audioPromptRef(), audioUrlTtlSeconds, tenantId);
        } else if (source.audioPromptRef() != null) {
            // Every other section's audioPromptRef is optional (only some Speaking task
            // types carry one) — presign when present, skip silently when absent. Powers
            // the same on-demand `/audio` endpoint LISTENING already uses. The returned
            // duration also feeds the dynamic-prep-timing branch below, for whichever of
            // the 5 audio-prompt types this item happens to be — one call serves both needs.
            audioDurationSeconds = resolveAudioUrl(item, source.audioPromptRef(), audioUrlTtlSeconds, tenantId);
        }

        if (DESCRIBE_IMAGE_TASK_TYPE.equals(taskTypeCode)) {
            // DESCRIBE_IMAGE's imagePromptRef is mandatory — same fail-loud rationale
            // as LISTENING's audioPromptRef above (an authoring data problem, not a
            // client error).
            if (source.imagePromptRef() == null) {
                throw new MissingImagePromptException();
            }
            resolveImageUrl(item, source.imagePromptRef(), audioUrlTtlSeconds, tenantId);
        } else if (source.imagePromptRef() != null) {
            // Every other task type's imagePromptRef is optional/unused today —
            // presign when present anyway (mirrors audioPromptRef's own optional-elsewhere
            // branch), skip silently when absent.
            resolveImageUrl(item, source.imagePromptRef(), audioUrlTtlSeconds, tenantId);
        }

        // `jsonTiming.preListenSeconds()` being non-null IS the signal that this
        // task type computes prep dynamically from real audio duration instead
        // of a static prepSeconds — config presence drives the branch, not a
        // second, hardcoded task-type list (unchanged principle from before
        // Phase 3; only preRecordSeconds's SOURCE moved, from this same JSON
        // entry to the pinned template's prepSeconds column).
        if (isAudioPromptType) {
            if (audioDurationSeconds == null) {
                throw new MissingAudioDurationException();
            }
            if (templateItem == null) {
                // All 5 audio-prompt types are always `scored` and therefore
                // always present in any ACTIVE-and-valid template (FR-05) — this
                // would mean the pinned template itself is inconsistent.
                throw new TaskTimingNotConfiguredException();
            }
            int preRecordSeconds = templateItem.prepSeconds();
            item.setPreListenSeconds(jsonTiming.preListenSeconds());
            item.setPreRecordSeconds(preRecordSeconds);
            item.setPrepSeconds(jsonTiming.preListenSeconds() + audioDurationSeconds + preRecordSeconds);
        } else if (templateItem != null) {
            item.setPrepSeconds(templateItem.prepSeconds());
        } else {
            item.setPrepSeconds(jsonTiming.prepSeconds());
        }
        return item;
    }

    /**
     * The source snapshot is the only runtime-provenance source. The pinned
     * score template is still read for its existing timing contract, but it is
     * never consulted to reconstruct renderer/scoring behavior.
     */
    private void copyRuntimeContract(SnapshotContentResponse.Item source, PinnedItem target, String taskTypeCode) {
        TaskRuntimeProfileDescriptor runtime = source.runtime();
        if (runtime == null) {
            if (source.runtimeMappingStatus() != null
                    && !TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_LEGACY
                            .equals(source.runtimeMappingStatus())) {
                throw new ExamCapabilityException(AttemptConstants.EXAM_CONFIGURATION_NOT_COMPATIBLE, List.of());
            }
            if (source.runtimeMappingVersion() != null
                    && !TaskRuntimeContractConstants.MAPPING_VERSION_LEGACY.equals(source.runtimeMappingVersion())) {
                throw new ExamCapabilityException(AttemptConstants.EXAM_CONFIGURATION_NOT_COMPATIBLE, List.of());
            }
            // Rows created before Phase 4 are explicitly grandfathered as a
            // named historical mapping. The enum/alias compatibility boundary
            // above is the only mapping used; no mutable catalog lookup occurs.
            target.setRuntimeMappingVersion(TaskRuntimeContractConstants.MAPPING_VERSION_LEGACY);
            target.setRuntimeMappingStatus(TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_LEGACY);
            return;
        }
        if (!TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_CANONICAL
                .equals(source.runtimeMappingStatus())
                || !TaskRuntimeContractConstants.MAPPING_VERSION_CANONICAL.equals(source.runtimeMappingVersion())) {
            throw new ExamCapabilityException(AttemptConstants.EXAM_CONFIGURATION_NOT_COMPATIBLE, List.of());
        }
        try {
            if (!taskTypeCode.equals(runtime.taskTypeCode())
                    || !TaskRuntimeProfileRegistry.descriptorFor(taskTypeCode).equals(runtime)) {
                throw new IllegalArgumentException("runtime profile is not an allowlisted match");
            }
        } catch (RuntimeException ex) {
            throw new ExamCapabilityException(AttemptConstants.EXAM_CONFIGURATION_NOT_COMPATIBLE, List.of());
        }
        target.pinRuntimeProfile(runtime, TaskRuntimeContractConstants.MAPPING_VERSION_CANONICAL,
                TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_CANONICAL);
    }

    private Integer resolveAudioUrl(PinnedItem item, UUID audioPromptRef, long audioUrlTtlSeconds, UUID tenantId) {
        PresignedDownloadResponse presigned = mediaService.presignGet(audioPromptRef, audioUrlTtlSeconds, tenantId);
        item.setAudioUrl(presigned.url());
        item.setAudioUrlExpiresAt(Instant.now().plusSeconds(presigned.expiresInSeconds()));
        return presigned.durationSeconds();
    }

    /**
     * Mirrors {@link #resolveAudioUrl} but simpler: no duration to extract (a
     * static image has none), so returns {@code void}. {@code imageUrlTtlSeconds}
     * reuses the same {@code audioUrlTtlSeconds} value the caller already computed
     * from the session window — deliberately media-type-agnostic, not a
     * copy-paste oversight.
     */
    private void resolveImageUrl(PinnedItem item, UUID imagePromptRef, long imageUrlTtlSeconds, UUID tenantId) {
        PresignedDownloadResponse presigned = mediaService.presignGet(imagePromptRef, imageUrlTtlSeconds, tenantId);
        item.setImageUrl(presigned.url());
        item.setImageUrlExpiresAt(Instant.now().plusSeconds(presigned.expiresInSeconds()));
    }
}
