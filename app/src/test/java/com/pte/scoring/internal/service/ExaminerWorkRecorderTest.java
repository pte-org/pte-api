package com.pte.scoring.internal.service;

import com.pte.scoring.domain.enums.ExaminerGradingOperation;
import com.pte.scoring.dto.response.ExaminerCommandReceiptResponse;
import com.pte.scoring.dto.response.ExaminerGradingAuditMetadata;
import com.pte.shared.audit.AuditChange;
import com.pte.shared.audit.AuditHistoryEntry;
import com.pte.shared.audit.AuditHistoryReader;
import com.pte.shared.audit.AuditLogChangeWriter;
import com.pte.shared.idempotency.IdempotencyDecision;
import com.pte.shared.idempotency.IdempotencyKeyService;
import com.pte.shared.idempotency.IdempotencyOutcome;
import com.pte.shared.idempotency.domain.IdempotencyScope;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.pte.scoring.internal.exception.ExaminerReceiptConflictException;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Examiner grading commands keep their receipt in the shared idempotency table and their history in the audit trail. */
@ExtendWith(MockitoExtension.class)
class ExaminerWorkRecorderTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID examinerId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();
    private final CurrentUser examiner = new CurrentUser(examinerId, tenantId, List.of("EXAMINER"));

    @Mock
    private IdempotencyKeyService idempotencyKeyService;
    @Mock
    private AuditLogChangeWriter auditWriter;
    @Mock
    private AuditHistoryReader auditReader;

    private ExaminerWorkRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new ExaminerWorkRecorder(idempotencyKeyService, auditWriter, auditReader, jsonMapper);
    }

    @Test
    void aNewRequestIsClaimedUnderTheExaminerCommandScope() {
        when(idempotencyKeyService.begin(eq(IdempotencyScope.EXAMINER_COMMAND), any(), eq(tenantId), eq("fp"), any()))
                .thenReturn(new IdempotencyDecision(IdempotencyOutcome.NEW, null));

        assertThat(recorder.replayOrBegin(examiner, assignmentId, requestId, "fp", Instant.now())).isEmpty();

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(idempotencyKeyService).begin(eq(IdempotencyScope.EXAMINER_COMMAND), key.capture(), eq(tenantId),
                eq("fp"), any());
        assertThat(key.getValue()).contains(assignmentId.toString(), examinerId.toString(), requestId.toString());
    }

    @Test
    void anIdenticalRetryGetsTheStoredReceiptBack() {
        ExaminerCommandReceiptResponse stored = receipt();
        when(idempotencyKeyService.begin(eq(IdempotencyScope.EXAMINER_COMMAND), any(), eq(tenantId), eq("fp"), any()))
                .thenReturn(new IdempotencyDecision(IdempotencyOutcome.REPLAY, jsonMapper.writeValueAsString(stored)));

        assertThat(recorder.replayOrBegin(examiner, assignmentId, requestId, "fp", Instant.now())).contains(stored);
    }

    @Test
    void theSameRequestIdWithADifferentPayloadIsAConflict() {
        when(idempotencyKeyService.begin(eq(IdempotencyScope.EXAMINER_COMMAND), any(), eq(tenantId), eq("other"),
                any())).thenReturn(new IdempotencyDecision(IdempotencyOutcome.CONFLICT, null));

        assertThatThrownBy(() -> recorder.replayOrBegin(examiner, assignmentId, requestId, "other", Instant.now()))
                .isInstanceOf(ExaminerReceiptConflictException.class);
    }

    @Test
    void aCompletedCommandReceiptCanBeLookedUpByItsRequestId() {
        ExaminerCommandReceiptResponse stored = receipt();
        recorder.complete(examiner, assignmentId, requestId, stored);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(idempotencyKeyService).complete(eq(IdempotencyScope.EXAMINER_COMMAND), any(), eq(tenantId),
                json.capture());
        when(idempotencyKeyService.findResult(eq(IdempotencyScope.EXAMINER_COMMAND), any(), eq(tenantId)))
                .thenReturn(Optional.of(json.getValue()));

        assertThat(recorder.findReceipt(examiner, assignmentId, requestId)).contains(stored);
    }

    @Test
    void everyRecordedCommandBecomesARowOfTheAssignmentAuditTrail() {
        ExaminerGradingAuditMetadata metadata = new ExaminerGradingAuditMetadata(examinerId, "EXAMINER",
                assignmentId, 70, 80, 1L, "Score updated");
        ExaminerWorkRecorder.Entry entry = new ExaminerWorkRecorder.Entry(UUID.randomUUID(),
                ExaminerGradingOperation.SCORE_CHANGE, 3L, 4L, metadata);

        recorder.record(examiner, assignmentId, requestId, "summary", entry);

        ArgumentCaptor<AuditChange> change = ArgumentCaptor.forClass(AuditChange.class);
        verify(auditWriter).record(eq(examiner), change.capture());
        assertThat(change.getValue().aggregateType()).isEqualTo("EXAMINER_WORK");
        assertThat(change.getValue().aggregateId()).isEqualTo(assignmentId.toString());
        assertThat(change.getValue().action()).isEqualTo("SCORE_CHANGE");
        assertThat(change.getValue().requestPublicId()).isEqualTo(requestId);
        AuditHistoryEntry row = new AuditHistoryEntry(UUID.randomUUID(), examinerId, "EXAMINER", requestId,
                "summary", null, change.getValue().afterStateJson(), Instant.now());
        assertThat(recorder.readEntry(row)).isEqualTo(entry);
    }

    private ExaminerCommandReceiptResponse receipt() {
        return new ExaminerCommandReceiptResponse(requestId, ExaminerGradingOperation.SAVE_DRAFT,
                Instant.parse("2026-10-24T09:00:00Z"), 2L, UUID.randomUUID(), 1L, List.of());
    }
}
