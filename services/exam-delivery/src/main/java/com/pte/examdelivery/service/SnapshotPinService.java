package com.pte.examdelivery.service;

import com.pte.examdelivery.client.AuthoringClient;
import com.pte.examdelivery.client.MediaClient;
import com.pte.examdelivery.client.SchedulingClient;
import com.pte.examdelivery.client.dto.AuthoringSnapshotContentResponse;
import com.pte.examdelivery.client.dto.MediaPresignedDownloadResponse;
import com.pte.examdelivery.client.dto.SchedulingEntitlementResponse;
import com.pte.examdelivery.config.TaskTimingConfig;
import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.PinnedExamSnapshot;
import com.pte.examdelivery.domain.PinnedItem;
import com.pte.examdelivery.domain.exception.AudioResolutionFailedException;
import com.pte.examdelivery.domain.exception.EntitlementCheckFailedException;
import com.pte.examdelivery.domain.exception.MissingAudioPromptException;
import com.pte.examdelivery.domain.exception.SnapshotContentFetchFailedException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the ONE guarded attempt-create pull (phase-05 design constraint):
 * scheduling for entitlement+composition, authoring for full snapshot content —
 * then deep-copies the result into a self-contained {@link PinnedExamSnapshot}.
 * After this returns, exam-delivery never calls out again for this attempt.
 *
 * <p>Composition selects task TYPES (not individual items — matches scheduling's
 * model): every snapshot item whose type is included gets pinned, in the
 * source snapshot's original order. A composition {@code timingOverrideSeconds}
 * for a type overrides that type's RESPONSE time only; prep stays the
 * task-type default (practice mode shortens answering time, not think time).
 */
@Service
public class SnapshotPinService {

    private static final String LISTENING_SECTION = "LISTENING";
    private static final long AUDIO_URL_GRACE_SECONDS = 60;

    private final SchedulingClient schedulingClient;
    private final AuthoringClient authoringClient;
    private final MediaClient mediaClient;
    private final TaskTimingConfig taskTimingConfig;

    public SnapshotPinService(SchedulingClient schedulingClient, AuthoringClient authoringClient,
                              MediaClient mediaClient, TaskTimingConfig taskTimingConfig) {
        this.schedulingClient = schedulingClient;
        this.authoringClient = authoringClient;
        this.mediaClient = mediaClient;
        this.taskTimingConfig = taskTimingConfig;
    }

    public PinnedExamSnapshot pin(ExamAttempt attempt, UUID sessionPublicId, UUID studentPublicId) {
        SchedulingEntitlementResponse entitlement = schedulingClient.checkEntitlement(sessionPublicId, studentPublicId);
        if (entitlement == null || entitlement.policy() == null || entitlement.policy().replayPolicyType() == null
                || entitlement.policy().answerIntegrityLevel() == null) {
            throw new EntitlementCheckFailedException();
        }
        AuthoringSnapshotContentResponse content = authoringClient.fetchContent(entitlement.snapshotPublicId());
        if (content == null) {
            throw new SnapshotContentFetchFailedException();
        }

        Map<String, Integer> responseOverrideByTaskType = entitlement.composition().stream()
                .filter(item -> item.timingOverrideSeconds() != null)
                .collect(Collectors.toMap(SchedulingEntitlementResponse.CompositionItem::taskType,
                        SchedulingEntitlementResponse.CompositionItem::timingOverrideSeconds, (a, b) -> a));
        Map<String, Integer> maxPlayCountByTaskType = entitlement.composition().stream()
                .filter(item -> item.maxPlayCount() != null)
                .collect(Collectors.toMap(SchedulingEntitlementResponse.CompositionItem::taskType,
                        SchedulingEntitlementResponse.CompositionItem::maxPlayCount, (a, b) -> a));
        Set<String> includedTaskTypes = entitlement.composition().stream()
                .map(SchedulingEntitlementResponse.CompositionItem::taskType)
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

        long audioUrlTtlSeconds = Duration.between(entitlement.opensAt(), entitlement.closesAt()).getSeconds()
                + AUDIO_URL_GRACE_SECONDS;

        content.items().stream()
                .filter(item -> includedTaskTypes.contains(item.taskType()))
                .sorted(Comparator.comparingInt(AuthoringSnapshotContentResponse.Item::orderIndex))
                .forEach(item -> pinned.addItem(toPinnedItem(item, responseOverrideByTaskType, maxPlayCountByTaskType,
                        audioUrlTtlSeconds, entitlement.tenantId())));

        return pinned;
    }

    private PinnedItem toPinnedItem(AuthoringSnapshotContentResponse.Item source,
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
        item.setPrepSeconds(timing.prepSeconds());
        item.setResponseSeconds(responseSeconds);
        item.setMaxPlayCountOverride(maxPlayCountByTaskType.get(source.taskType()));

        if (LISTENING_SECTION.equals(source.section())) {
            // LISTENING's audioPromptRef is mandatory — a listening item with none is an
            // authoring data problem, not a client error, and must keep failing loudly.
            if (source.audioPromptRef() == null) {
                throw new MissingAudioPromptException();
            }
            resolveAudioUrl(item, source.audioPromptRef(), audioUrlTtlSeconds, tenantId);
        } else if (source.audioPromptRef() != null) {
            // Every other section's audioPromptRef is optional (only some Speaking task
            // types carry one) — presign when present, skip silently when absent. This
            // powers the same on-demand `/audio` endpoint LISTENING already uses
            // (AttemptService.playAudio reads PinnedItem.audioUrl regardless of section),
            // just for Speaking items too (plans/phat-speaking-audio-prompt-e2e).
            resolveAudioUrl(item, source.audioPromptRef(), audioUrlTtlSeconds, tenantId);
        }
        return item;
    }

    private void resolveAudioUrl(PinnedItem item, UUID audioPromptRef, long audioUrlTtlSeconds, UUID tenantId) {
        MediaPresignedDownloadResponse presigned = mediaClient.presignGet(audioPromptRef, audioUrlTtlSeconds, tenantId);
        if (presigned == null) {
            throw new AudioResolutionFailedException();
        }
        item.setAudioUrl(presigned.url());
        item.setAudioUrlExpiresAt(Instant.now().plusSeconds(presigned.expiresInSeconds()));
    }
}
