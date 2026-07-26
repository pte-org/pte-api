package com.pte.examdelivery.service;

import com.pte.examdelivery.client.AuthoringClient;
import com.pte.examdelivery.client.SchedulingClient;
import com.pte.examdelivery.client.dto.AuthoringSnapshotContentResponse;
import com.pte.examdelivery.client.dto.SchedulingEntitlementResponse;
import com.pte.examdelivery.config.TaskTimingConfig;
import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.PinnedExamSnapshot;
import com.pte.examdelivery.domain.PinnedItem;
import com.pte.examdelivery.domain.exception.EntitlementCheckFailedException;
import com.pte.examdelivery.domain.exception.SnapshotContentFetchFailedException;
import org.springframework.stereotype.Service;

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

    private final SchedulingClient schedulingClient;
    private final AuthoringClient authoringClient;
    private final TaskTimingConfig taskTimingConfig;

    public SnapshotPinService(SchedulingClient schedulingClient, AuthoringClient authoringClient,
                              TaskTimingConfig taskTimingConfig) {
        this.schedulingClient = schedulingClient;
        this.authoringClient = authoringClient;
        this.taskTimingConfig = taskTimingConfig;
    }

    public PinnedExamSnapshot pin(ExamAttempt attempt, UUID sessionPublicId, UUID studentPublicId) {
        SchedulingEntitlementResponse entitlement = schedulingClient.checkEntitlement(sessionPublicId, studentPublicId);
        if (entitlement == null) {
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
        Set<String> includedTaskTypes = entitlement.composition().stream()
                .map(SchedulingEntitlementResponse.CompositionItem::taskType)
                .collect(Collectors.toSet());

        PinnedExamSnapshot pinned = new PinnedExamSnapshot();
        pinned.setAttempt(attempt);
        pinned.setSourceSnapshotPublicId(content.publicId());
        pinned.setSourceSessionPublicId(sessionPublicId);
        pinned.setTenantId(entitlement.tenantId());

        content.items().stream()
                .filter(item -> includedTaskTypes.contains(item.taskType()))
                .sorted(Comparator.comparingInt(AuthoringSnapshotContentResponse.Item::orderIndex))
                .forEach(item -> pinned.addItem(toPinnedItem(item, responseOverrideByTaskType)));

        return pinned;
    }

    private PinnedItem toPinnedItem(AuthoringSnapshotContentResponse.Item source,
                                    Map<String, Integer> responseOverrideByTaskType) {
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
        return item;
    }
}
