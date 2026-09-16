package com.pte.session.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.session.domain.ExamSession;
import com.pte.session.internal.dto.request.CompositionItemRequest;
import com.pte.session.internal.dto.request.SetCompositionRequest;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.exception.TaskTypeNotInSnapshotException;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Covers the Phase 06 boundary rule: composition validates against the
 * snapshot read fresh from {@link AssessmentService#getSummary} each call —
 * no local {@code SnapshotRef} cache table (source: services/scheduling's
 * SnapshotRefService, eliminated in the monolith).
 */
@ExtendWith(MockitoExtension.class)
class CompositionServiceTest {

    @Mock
    private SessionLifecycleService sessionLifecycleService;
    @Mock
    private AssessmentService assessmentService;

    private CompositionService compositionService;
    private CurrentUser caller;

    @BeforeEach
    void setUp() {
        compositionService = new CompositionService(sessionLifecycleService, assessmentService);
        caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));
    }

    private ExamSession sessionWithSnapshot(UUID snapshotPublicId) {
        ExamSession session = new ExamSession();
        session.setPublicId(UUID.randomUUID());
        session.setSnapshotPublicId(snapshotPublicId);
        return session;
    }

    @Test
    void setComposition_taskTypeInSnapshot_accepted() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID snapshotPublicId = UUID.randomUUID();
        ExamSession session = sessionWithSnapshot(snapshotPublicId);
        when(sessionLifecycleService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(assessmentService.getSummary(snapshotPublicId)).thenReturn(new SnapshotResponse(
                snapshotPublicId, "Mock Test A", 1, UUID.randomUUID(), UUID.randomUUID(), 1, null,
                List.of(new SnapshotResponse.Item(0, "READING", "MC_READING_SINGLE", "title"))));

        SessionResponse response = compositionService.setComposition(sessionPublicId,
                new SetCompositionRequest(List.of(
                        new CompositionItemRequest("MC_READING_SINGLE", "READING", 0, null, null))),
                caller);

        assertThat(response.composition()).hasSize(1);
        assertThat(response.composition().get(0).taskType()).isEqualTo("MC_READING_SINGLE");
    }

    @Test
    void setComposition_taskTypeNotInSnapshot_rejected() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID snapshotPublicId = UUID.randomUUID();
        ExamSession session = sessionWithSnapshot(snapshotPublicId);
        when(sessionLifecycleService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(assessmentService.getSummary(snapshotPublicId)).thenReturn(new SnapshotResponse(
                snapshotPublicId, "Mock Test A", 1, UUID.randomUUID(), UUID.randomUUID(), 1, null,
                List.of(new SnapshotResponse.Item(0, "READING", "MC_READING_SINGLE", "title"))));

        assertThatThrownBy(() -> compositionService.setComposition(sessionPublicId,
                new SetCompositionRequest(List.of(
                        new CompositionItemRequest("WRITE_ESSAY", "WRITING", 0, null, null))),
                caller))
                .isInstanceOf(TaskTypeNotInSnapshotException.class);
    }
}
