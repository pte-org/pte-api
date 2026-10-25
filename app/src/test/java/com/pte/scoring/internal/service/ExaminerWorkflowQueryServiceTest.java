package com.pte.scoring.internal.service;

import com.pte.assessment.ExamDefinitionService;
import com.pte.attempt.AttemptService;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.enums.ExaminerGradingOperation;
import com.pte.scoring.dto.response.ExaminerGradingAuditMetadata;
import com.pte.scoring.dto.response.ExaminerHistoryResponse;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.GradingIssueRepository;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.shared.audit.AuditHistoryEntry;
import com.pte.shared.audit.AuditHistoryReader;
import com.pte.shared.audit.AuditLogChangeWriter;
import com.pte.shared.idempotency.IdempotencyKeyService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExaminerWorkflowQueryServiceTest {

    @Mock
    private AuditHistoryReader auditHistoryReader;
    @Mock
    private GradingIssueRepository issueRepository;
    @Mock
    private GradingAssignmentRepository assignmentRepository;
    @Mock
    private ExaminerEvaluationRepository scoreRepository;
    @Mock
    private SessionGradingGateway gateway;
    @Mock
    private AttemptService attemptService;
    @Mock
    private AiEvaluationRepository answerRepository;
    @Mock
    private ExamDefinitionService examDefinitionService;

    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
    private ExaminerWorkflowQueryService queryService;

    @BeforeEach
    void setUp() {
        ExaminerWorkRecorder recorder = new ExaminerWorkRecorder(mock(IdempotencyKeyService.class),
                mock(AuditLogChangeWriter.class), auditHistoryReader, jsonMapper);
        queryService = new ExaminerWorkflowQueryService(
                recorder, issueRepository,
                assignmentRepository, scoreRepository, gateway, attemptService, answerRepository,
                examDefinitionService
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

        GradingAssignment assignment = new GradingAssignment(tenantId, sessionPublicId, attemptPublicId, examinerId, 5, UUID.randomUUID(), Instant.now());
        UUID assignmentPublicId = UUID.randomUUID();
        org.springframework.test.util.ReflectionTestUtils.setField(assignment, "publicId", assignmentPublicId);

        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicIdAndDeletedFalse(
                tenantId, sessionPublicId, attemptPublicId, examinerId))
                .thenReturn(Optional.of(assignment));

        assignment.start(Instant.now());

        UUID answerPublicId = UUID.randomUUID();
        ExaminerWorkRecorder.Entry entry = new ExaminerWorkRecorder.Entry(answerPublicId,
                ExaminerGradingOperation.SCORE_CHANGE, 2L, 3L, new ExaminerGradingAuditMetadata(examinerId,
                        "EXAMINER", assignmentPublicId, 75, 85, 1L, "Revised grammar assessment"));
        Instant occurredAt = Instant.parse("2026-10-24T08:00:00Z");
        AuditHistoryEntry row = new AuditHistoryEntry(UUID.randomUUID(), examinerId, "EXAMINER", UUID.randomUUID(),
                "summary", null, jsonMapper.writeValueAsString(entry), occurredAt);
        when(auditHistoryReader.history(tenantId, "EXAMINER_WORK", assignmentPublicId.toString(), 0, 20))
                .thenReturn(new PagedResult<>(List.of(row), new PageMeta(0, 20, 1, 1, true, true, false, false)));

        ExaminerHistoryResponse history = queryService.getHistory(sessionPublicId, attemptPublicId, PageRequest.of(0, 20), examiner);

        assertThat(history.items()).hasSize(1);
        var item = history.items().getFirst();
        assertThat(item.occurredAt()).isEqualTo(occurredAt);
        assertThat(item.answerPublicId()).isEqualTo(answerPublicId);
        assertThat(item.workVersion()).isEqualTo(2L);
        assertThat(item.draftVersion()).isEqualTo(3L);
        assertThat(item.operation()).isEqualTo(ExaminerGradingOperation.SCORE_CHANGE);
        assertThat(item.actorRole()).isEqualTo("EXAMINER");
        assertThat(item.previousScore()).isEqualTo(75);
        assertThat(item.newScore()).isEqualTo(85);
        assertThat(item.rubricVersion()).isEqualTo(1);
        assertThat(item.reason()).isEqualTo("Revised grammar assessment");
    }
}
