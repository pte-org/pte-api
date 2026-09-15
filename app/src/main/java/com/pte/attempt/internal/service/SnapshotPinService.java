package com.pte.attempt.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.PinnedExamSnapshot;
import com.pte.attempt.domain.PinnedItem;
import com.pte.attempt.internal.config.TaskTimingConfig;
import com.pte.attempt.internal.exception.MissingAudioDurationException;
import com.pte.attempt.internal.exception.MissingAudioPromptException;
import com.pte.attempt.internal.exception.MissingImagePromptException;
import com.pte.media.MediaService;
import com.pte.media.dto.response.PresignedDownloadResponse;
import com.pte.session.SessionService;
import com.pte.session.dto.response.EntitlementResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the ONE guarded attempt-create call (Phase 07 design
 * constraint): {@code session} for entitlement+composition, {@code
 * assessment} for full snapshot content, {@code media} for presigned
 * audio/image URLs — each exactly once, in-process — then deep-copies the
 * result into a self-contained {@link PinnedExamSnapshot}. After this
 * returns, attempt never calls another module again for this attempt.
 *
 * <p>Composition selects task TYPES (not individual items — matches
 * session's model): every snapshot item whose type is included gets pinned,
 * in the source snapshot's original order. A composition {@code
 * timingOverrideSeconds} for a type overrides that type's RESPONSE time
 * only; prep stays the task-type default (practice mode shortens answering
 * time, not think time).
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

    public SnapshotPinService(SessionService sessionService, AssessmentService assessmentService,
                              MediaService mediaService, TaskTimingConfig taskTimingConfig) {
        this.sessionService = sessionService;
        this.assessmentService = assessmentService;
        this.mediaService = mediaService;
        this.taskTimingConfig = taskTimingConfig;
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

        Map<String, Integer> responseOverrideByTaskType = entitlement.composition().stream()
                .filter(item -> item.timingOverrideSeconds() != null)
                .collect(Collectors.toMap(com.pte.session.dto.response.CompositionItemResponse::taskType,
                        com.pte.session.dto.response.CompositionItemResponse::timingOverrideSeconds, (a, b) -> a));
        Map<String, Integer> maxPlayCountByTaskType = entitlement.composition().stream()
                .filter(item -> item.maxPlayCount() != null)
                .collect(Collectors.toMap(com.pte.session.dto.response.CompositionItemResponse::taskType,
                        com.pte.session.dto.response.CompositionItemResponse::maxPlayCount, (a, b) -> a));
        Set<String> includedTaskTypes = entitlement.composition().stream()
                .map(com.pte.session.dto.response.CompositionItemResponse::taskType)
                .collect(Collectors.toSet());

        PinnedExamSnapshot pinned = new PinnedExamSnapshot();
        pinned.setAttempt(attempt);
        pinned.setSourceSnapshotPublicId(content.publicId());
        pinned.setSourceSessionPublicId(sessionPublicId);
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
                .filter(item -> includedTaskTypes.contains(item.taskType()))
                .sorted(Comparator.comparingInt(SnapshotContentResponse.Item::orderIndex))
                .forEach(item -> pinned.addItem(toPinnedItem(item, responseOverrideByTaskType, maxPlayCountByTaskType,
                        audioUrlTtlSeconds, entitlement.tenantId())));

        return pinned;
    }

    private PinnedItem toPinnedItem(SnapshotContentResponse.Item source,
                                    Map<String, Integer> responseOverrideByTaskType,
                                    Map<String, Integer> maxPlayCountByTaskType,
                                    long audioUrlTtlSeconds, UUID tenantId) {
        TaskTimingConfig.Timing timing = taskTimingConfig.timingFor(source.taskType());
        int responseSeconds = responseOverrideByTaskType.getOrDefault(source.taskType(), timing.responseSeconds());

        PinnedItem item = new PinnedItem();
        item.setOrderIndex(source.orderIndex());
        item.setSection(source.section());
        item.setTaskType(source.taskType());
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
        item.setMaxPlayCountOverride(maxPlayCountByTaskType.get(source.taskType()));

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

        if (DESCRIBE_IMAGE_TASK_TYPE.equals(source.taskType())) {
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

        // `timing.preListenSeconds()` (and preRecordSeconds, always set together —
        // see TaskTimingConfig.Timing's own doc comment) being non-null IS the
        // signal that this task type computes prep dynamically from real audio
        // duration instead of the static timing.prepSeconds() fallback — config
        // presence drives the branch, not a second, hardcoded task-type list.
        if (timing.preListenSeconds() != null) {
            if (audioDurationSeconds == null) {
                throw new MissingAudioDurationException();
            }
            item.setPreListenSeconds(timing.preListenSeconds());
            item.setPreRecordSeconds(timing.preRecordSeconds());
            item.setPrepSeconds(timing.preListenSeconds() + audioDurationSeconds + timing.preRecordSeconds());
        } else {
            item.setPrepSeconds(timing.prepSeconds());
        }
        return item;
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
