package com.pte.session.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.media.MediaService;
import com.pte.session.domain.ExamSession;
import com.pte.session.internal.dto.response.ExamPreviewResponse;
import com.pte.session.internal.exception.GenerationNotReadyException;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link SessionExamPreviewService#preview} correctly passes
 * {@code sourceQuestionPublicId} from {@link SnapshotContentResponse.Item}
 * through to {@link ExamPreviewResponse.Item} — the core change introduced
 * by the report-question-ticket feature.
 */
@ExtendWith(MockitoExtension.class)
class SessionExamPreviewServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock
    private SessionLifecycleService sessionLifecycleService;
    @Mock
    private AssessmentService assessmentService;
    @Mock
    private MediaService mediaService;

    private SessionExamPreviewService service;
    private CurrentUser caller;

    @BeforeEach
    void setUp() {
        service = new SessionExamPreviewService(
                sessionLifecycleService, assessmentService, mediaService,
                JsonMapper.builder().build());
        caller = new CurrentUser(UUID.randomUUID(), TENANT_ID, List.of("HOST_ADMIN"));
    }

    @Test
    void preview_sourceQuestionPublicId_isPassedThroughWhenSet() {
        UUID sessionId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();

        ExamSession session = sessionWithSnapshot(snapshotId);
        when(sessionLifecycleService.findOwned(sessionId, caller)).thenReturn(session);

        SnapshotContentResponse snapshot = snapshotWithItem(snapshotId, questionId);
        when(assessmentService.getFullContent(snapshotId)).thenReturn(snapshot);

        ExamPreviewResponse response = service.preview(sessionId, caller);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sourceQuestionPublicId()).isEqualTo(questionId);
    }

    @Test
    void preview_sourceQuestionPublicId_isPreservedAsNullWhenAbsent() {
        UUID sessionId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();

        ExamSession session = sessionWithSnapshot(snapshotId);
        when(sessionLifecycleService.findOwned(sessionId, caller)).thenReturn(session);

        SnapshotContentResponse snapshot = snapshotWithItem(snapshotId, null);
        when(assessmentService.getFullContent(snapshotId)).thenReturn(snapshot);

        ExamPreviewResponse response = service.preview(sessionId, caller);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sourceQuestionPublicId()).isNull();
    }

    @Test
    void preview_multipleItems_eachCarriesItsOwnSourceQuestionPublicId() {
        UUID sessionId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID firstQuestionId = UUID.randomUUID();
        UUID secondQuestionId = UUID.randomUUID();

        ExamSession session = sessionWithSnapshot(snapshotId);
        when(sessionLifecycleService.findOwned(sessionId, caller)).thenReturn(session);

        SnapshotContentResponse snapshot = new SnapshotContentResponse(
                snapshotId, "Multi Exam", 1, UUID.randomUUID(), 1, TENANT_ID,
                List.of(
                        buildContentItem(0, firstQuestionId),
                        buildContentItem(1, secondQuestionId)));
        when(assessmentService.getFullContent(snapshotId)).thenReturn(snapshot);

        ExamPreviewResponse response = service.preview(sessionId, caller);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).sourceQuestionPublicId()).isEqualTo(firstQuestionId);
        assertThat(response.items().get(1).sourceQuestionPublicId()).isEqualTo(secondQuestionId);
    }

    @Test
    void preview_mixedNullAndNonNullSourceIds_mappedCorrectly() {
        UUID sessionId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID presentId = UUID.randomUUID();

        ExamSession session = sessionWithSnapshot(snapshotId);
        when(sessionLifecycleService.findOwned(sessionId, caller)).thenReturn(session);

        SnapshotContentResponse snapshot = new SnapshotContentResponse(
                snapshotId, "Mixed Exam", 1, UUID.randomUUID(), 1, TENANT_ID,
                List.of(
                        buildContentItem(0, presentId),
                        buildContentItem(1, null)));
        when(assessmentService.getFullContent(snapshotId)).thenReturn(snapshot);

        ExamPreviewResponse response = service.preview(sessionId, caller);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).sourceQuestionPublicId()).isEqualTo(presentId);
        assertThat(response.items().get(1).sourceQuestionPublicId()).isNull();
    }

    @Test
    void preview_noSnapshot_throwsGenerationNotReadyException() {
        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setTenantId(TENANT_ID);
        // snapshotPublicId intentionally left null

        when(sessionLifecycleService.findOwned(sessionId, caller)).thenReturn(session);

        assertThatThrownBy(() -> service.preview(sessionId, caller))
                .isInstanceOf(GenerationNotReadyException.class);
    }

    // --- helpers ---

    private ExamSession sessionWithSnapshot(UUID snapshotPublicId) {
        ExamSession session = new ExamSession();
        session.setTenantId(TENANT_ID);
        session.setSnapshotPublicId(snapshotPublicId);
        return session;
    }

    private SnapshotContentResponse snapshotWithItem(UUID snapshotPublicId, UUID sourceQuestionPublicId) {
        return new SnapshotContentResponse(
                snapshotPublicId, "Test Exam", 1, UUID.randomUUID(), 1, TENANT_ID,
                List.of(buildContentItem(0, sourceQuestionPublicId)));
    }

    private SnapshotContentResponse.Item buildContentItem(int orderIndex, UUID sourceQuestionPublicId) {
        return new SnapshotContentResponse.Item(
                orderIndex,
                "READING",
                "MC_READING_SINGLE",
                "Sample Question " + orderIndex,
                "What is the main idea?",
                null,   // audioPromptRef
                null,   // imagePromptRef
                null,   // referenceAnswerText
                null,   // correctAnswerText
                null,   // minWordCount
                null,   // maxWordCount
                null,   // optionsJson
                "MC_READING_SINGLE",
                "Multiple Choice (Single)",
                "MC_READING_SINGLE",
                null,   // runtime
                null,   // runtimeMappingVersion
                null,   // runtimeMappingStatus
                sourceQuestionPublicId);
    }
}
