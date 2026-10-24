package com.pte.scoring.internal.service;

import com.pte.assessment.ExamDefinitionService;
import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.ExaminerGradingEvent;
import com.pte.scoring.domain.ExaminerGradingWork;
import com.pte.scoring.domain.enums.ExaminerGradingOperation;
import com.pte.scoring.dto.response.ExaminerHistoryResponse;
import com.pte.scoring.internal.repository.ExaminerAnswerDraftRepository;
import com.pte.scoring.internal.repository.ExaminerAnswerScoreRepository;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.scoring.internal.repository.ExaminerGradingEventRepository;
import com.pte.scoring.internal.repository.ExaminerGradingIssueRepository;
import com.pte.scoring.internal.repository.ExaminerGradingWorkRepository;
import com.pte.scoring.internal.repository.GradingCohortMemberRepository;
import com.pte.scoring.internal.repository.GradingCohortRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExaminerWorkflowQueryServiceTest {

    @Mock
    private ExaminerGradingWorkRepository workRepository;
    @Mock
    private ExaminerAnswerDraftRepository draftRepository;
    @Mock
    private ExaminerGradingEventRepository eventRepository;
    @Mock
    private ExaminerGradingIssueRepository issueRepository;
    @Mock
    private ExaminerAttemptAssignmentRepository assignmentRepository;
    @Mock
    private ExaminerAnswerScoreRepository scoreRepository;
    @Mock
    private ScoringSessionStateRepository sessionStateRepository;
    @Mock
    private GradingCohortRepository cohortRepository;
    @Mock
    private GradingCohortMemberRepository memberRepository;
    @Mock
    private ScoringAnswerRepository answerRepository;
    @Mock
    private ExamDefinitionService examDefinitionService;

    private final ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
    private ExaminerWorkflowQueryService queryService;

    @BeforeEach
    void setUp() {
        queryService = new ExaminerWorkflowQueryService(
                workRepository, draftRepository, eventRepository, issueRepository,
                assignmentRepository, scoreRepository, sessionStateRepository,
                cohortRepository, memberRepository, answerRepository,
                examDefinitionService, objectMapper
        );
    }

    @Test
    void getHistoryThrowsNotFoundWhenAssignmentDoesNotExistForExaminer() {
        UUID tenantId = UUID.randomUUID();
        UUID examinerId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        CurrentUser examiner = new CurrentUser(examinerId, tenantId, List.of("EXAMINER"));

        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicIdAndDeletedFalse(
                tenantId, sessionPublicId, attemptPublicId, examinerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getHistory(sessionPublicId, attemptPublicId, PageRequest.of(0, 20), examiner))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void getHistoryReturnsEnrichedAuditEventsWithMetadata() {
        UUID tenantId = UUID.randomUUID();
        UUID examinerId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        CurrentUser examiner = new CurrentUser(examinerId, tenantId, List.of("EXAMINER"));

        ExaminerAttemptAssignment assignment = new ExaminerAttemptAssignment(
                UUID.randomUUID(), tenantId, sessionPublicId, attemptPublicId, examinerId, 5, UUID.randomUUID(), Instant.now());
        UUID assignmentPublicId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(assignment, "publicId", assignmentPublicId);

        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicIdAndDeletedFalse(
                tenantId, sessionPublicId, attemptPublicId, examinerId))
                .thenReturn(Optional.of(assignment));

        ExaminerGradingWork work = new ExaminerGradingWork(
                tenantId, sessionPublicId, attemptPublicId, assignmentPublicId, examinerId, Instant.now());
        org.springframework.test.util.ReflectionTestUtils.setField(work, "id", 100L);

        when(workRepository.findByTenantIdAndAssignmentPublicIdAndDeletedFalse(tenantId, assignmentPublicId))
                .thenReturn(Optional.of(work));

        String metadataJson = "{\"actorPublicId\":\"" + examinerId + "\",\"actorRole\":\"EXAMINER\",\"assignmentPublicId\":\""
                + assignmentPublicId + "\",\"previousScore\":75,\"newScore\":85,\"rubricVersion\":1,\"reason\":\"Revised grammar assessment\"}";

        ExaminerGradingEvent event = new ExaminerGradingEvent(
                tenantId, 100L, UUID.randomUUID(), ExaminerGradingOperation.SCORE_CHANGE,
                2L, 3L, metadataJson, Instant.now());

        when(eventRepository.findByTenantIdAndWorkId(
                eq(tenantId), eq(100L), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(event), PageRequest.of(0, 20), 1));

        ExaminerHistoryResponse history = queryService.getHistory(sessionPublicId, attemptPublicId, PageRequest.of(0, 20), examiner);

        assertThat(history.items()).hasSize(1);
        var item = history.items().getFirst();
        assertThat(item.operation()).isEqualTo(ExaminerGradingOperation.SCORE_CHANGE);
        assertThat(item.actorRole()).isEqualTo("EXAMINER");
        assertThat(item.previousScore()).isEqualTo(75);
        assertThat(item.newScore()).isEqualTo(85);
        assertThat(item.rubricVersion()).isEqualTo(1);
        assertThat(item.reason()).isEqualTo("Revised grammar assessment");
    }
}
