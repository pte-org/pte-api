package com.pte.attempt.internal.service;

import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.attempt.internal.service.cache.ExamItemView;
import com.pte.itembank.TaskRuntimeContractConstants;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.session.SessionService;
import com.pte.session.dto.response.EntitlementResponse;
import com.pte.session.dto.response.ExamPolicyResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * A scripted exam for lifecycle tests: a mocked {@link ExamContentService} answering from a mutable item list,
 * so a test describes the exam by adding items and never touches Redis, the loader or the exam version.
 */
public final class FakeExam {

    final ExamContentService contentService = mock(ExamContentService.class);
    final List<ExamItemView> items = new ArrayList<>();

    FakeExam() {
        lenient().when(contentService.items(any())).thenAnswer(invocation -> List.copyOf(items));
        lenient().when(contentService.item(any(), any())).thenAnswer(invocation -> {
            UUID itemId = invocation.getArgument(1);
            return items.stream().filter(item -> item.publicId().equals(itemId)).findFirst();
        });
        lenient().when(contentService.itemAt(any(), anyInt())).thenAnswer(invocation -> {
            int orderIndex = invocation.getArgument(1);
            return items.stream().filter(item -> item.orderIndex() == orderIndex).findFirst()
                    .orElseThrow(() -> new IllegalStateException("no item at " + orderIndex));
        });
    }

    /**
     * Scripts what starting an attempt reads: the entitlement from session, the exam header and the loaded items.
     * Returns the exam version id the started attempt is expected to point at.
     */
    UUID stubStart(SessionService sessionService, UUID sessionId, UUID studentId, UUID tenantId,
            String answerIntegrityLevel) {
        UUID versionId = UUID.randomUUID();
        UUID definitionId = UUID.randomUUID();
        Instant now = Instant.now();
        ExamPolicyResponse policy = new ExamPolicyResponse("UNLIMITED", null, false, false, answerIntegrityLevel,
                null);
        lenient().when(sessionService.checkEntitlement(sessionId, studentId)).thenReturn(new EntitlementResponse(
                sessionId, versionId, tenantId, now.minusSeconds(3600), now.plusSeconds(3600), policy));
        lenient().when(contentService.header(versionId)).thenReturn(
                new SnapshotContentResponse(versionId, "exam", 1, definitionId, 1, tenantId, List.of()));
        lenient().when(contentService.loadForStart(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> List.copyOf(items));
        return versionId;
    }

    ExamItemView add(int orderIndex, String section) {
        return add(view(orderIndex, section, "TASK", 0, 60));
    }

    ExamItemView add(ExamItemView item) {
        items.add(item);
        return item;
    }

    Optional<ExamItemView> find(UUID itemId) {
        return items.stream().filter(item -> item.publicId().equals(itemId)).findFirst();
    }

    public static ExamItemView view(int orderIndex, String section, String taskType, int prepSeconds,
            int responseSeconds) {
        return new ExamItemView(UUID.randomUUID(), orderIndex, section, taskType, "Title " + orderIndex,
                "Prompt " + orderIndex, null, null, null, null, null, null, "[]", prepSeconds, responseSeconds, null,
                null, null, null, null, taskType, null, TaskRuntimeContractConstants.MAPPING_VERSION_LEGACY,
                TaskRuntimeContractConstants.MAPPING_STATUS_RESOLVED_LEGACY, taskType);
    }

    public static ExamItemView withId(ExamItemView v, UUID id) {
        return copy(v, id, v.audioUrl(), v.audioUrlExpiresAt(), v.orderIndex());
    }

    public static ExamItemView withRuntime(ExamItemView v, TaskRuntimeProfileDescriptor runtime, String mappingVersion,
            String mappingStatus) {
        return new ExamItemView(v.publicId(), v.orderIndex(), v.section(), v.taskType(), v.title(), v.promptText(),
                v.audioPromptRef(), v.imagePromptRef(), v.referenceAnswerText(), v.correctAnswerText(),
                v.minWordCount(), v.maxWordCount(), v.optionsJson(), v.prepSeconds(), v.responseSeconds(),
                v.preListenSeconds(), v.preRecordSeconds(), v.imageUrl(), v.audioUrl(), v.audioUrlExpiresAt(),
                v.taskTypeCode(), runtime, mappingVersion, mappingStatus, v.taskTypeDisplayName());
    }

    public static ExamItemView withOrderIndex(ExamItemView v, int orderIndex) {
        return copy(v, v.publicId(), v.audioUrl(), v.audioUrlExpiresAt(), orderIndex);
    }

    public static ExamItemView withAudio(ExamItemView v, String url, Instant expiresAt) {
        return copy(v, v.publicId(), url, expiresAt, v.orderIndex());
    }

    private static ExamItemView copy(ExamItemView v, UUID id, String audioUrl, Instant audioUrlExpiresAt,
            int orderIndex) {
        return new ExamItemView(id, orderIndex, v.section(), v.taskType(), v.title(), v.promptText(),
                v.audioPromptRef(), v.imagePromptRef(), v.referenceAnswerText(), v.correctAnswerText(),
                v.minWordCount(), v.maxWordCount(), v.optionsJson(), v.prepSeconds(), v.responseSeconds(),
                v.preListenSeconds(), v.preRecordSeconds(), v.imageUrl(), audioUrl, audioUrlExpiresAt,
                v.taskTypeCode(), v.runtime(), v.runtimeMappingVersion(), v.runtimeMappingStatus(),
                v.taskTypeDisplayName());
    }
}
