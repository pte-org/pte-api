package com.pte.scoring.internal.service;

import com.pte.scoring.domain.enums.ExaminerGradingOperation;
import com.pte.scoring.dto.response.ExaminerCommandReceiptResponse;
import com.pte.scoring.dto.response.ExaminerGradingAuditMetadata;
import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.scoring.internal.exception.ExaminerReceiptConflictException;
import com.pte.shared.audit.AuditChange;
import com.pte.shared.audit.AuditHistoryEntry;
import com.pte.shared.audit.AuditHistoryReader;
import com.pte.shared.audit.AuditLogChangeWriter;
import com.pte.shared.idempotency.IdempotencyDecision;
import com.pte.shared.idempotency.IdempotencyKeyService;
import com.pte.shared.idempotency.IdempotencyOutcome;
import com.pte.shared.idempotency.domain.IdempotencyScope;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PagedResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Receipts and history of an examiner's grading commands. A command is deduplicated and its receipt kept in the
 * shared idempotency table; every applied command is a row of the audit trail of its assignment, which is also
 * what the examiner history endpoint reads. Scoring keeps no table of its own for either.
 */
@Component
public class ExaminerWorkRecorder {

    /** What one history row carries beyond the audit columns: the operation and the versions it produced. */
    public record Entry(UUID answerPublicId, ExaminerGradingOperation operation, long workVersion, Long draftVersion,
                 ExaminerGradingAuditMetadata metadata) {
    }

    private final IdempotencyKeyService idempotencyKeyService;
    private final AuditLogChangeWriter auditWriter;
    private final AuditHistoryReader auditReader;
    private final JsonMapper jsonMapper;

    public ExaminerWorkRecorder(IdempotencyKeyService idempotencyKeyService, AuditLogChangeWriter auditWriter,
                         AuditHistoryReader auditReader, JsonMapper jsonMapper) {
        this.idempotencyKeyService = idempotencyKeyService;
        this.auditWriter = auditWriter;
        this.auditReader = auditReader;
        this.jsonMapper = jsonMapper;
    }

    /** Claims the request id for a new command, or returns the stored receipt of the identical earlier one. */
    Optional<ExaminerCommandReceiptResponse> replayOrBegin(CurrentUser user, UUID assignmentPublicId,
            UUID requestPublicId, String fingerprint, Instant now) {
        IdempotencyDecision decision = idempotencyKeyService.begin(IdempotencyScope.EXAMINER_COMMAND,
                key(assignmentPublicId, user.userId(), requestPublicId), user.tenantId(), fingerprint, now);
        if (decision.outcome() == IdempotencyOutcome.CONFLICT) {
            throw new ExaminerReceiptConflictException();
        }
        if (decision.outcome() == IdempotencyOutcome.REPLAY) {
            return Optional.of(jsonMapper.readValue(decision.resultJson(), ExaminerCommandReceiptResponse.class));
        }
        return Optional.empty();
    }

    void complete(CurrentUser user, UUID assignmentPublicId, UUID requestPublicId,
            ExaminerCommandReceiptResponse receipt) {
        idempotencyKeyService.complete(IdempotencyScope.EXAMINER_COMMAND,
                key(assignmentPublicId, user.userId(), requestPublicId), user.tenantId(),
                jsonMapper.writeValueAsString(receipt));
    }

    Optional<ExaminerCommandReceiptResponse> findReceipt(CurrentUser user, UUID assignmentPublicId,
            UUID requestPublicId) {
        return idempotencyKeyService.findResult(IdempotencyScope.EXAMINER_COMMAND,
                        key(assignmentPublicId, user.userId(), requestPublicId), user.tenantId())
                .map(json -> jsonMapper.readValue(json, ExaminerCommandReceiptResponse.class));
    }

    void record(CurrentUser user, UUID assignmentPublicId, UUID requestPublicId, String summary, Entry entry) {
        auditWriter.record(user, new AuditChange(ExaminerScoringConstants.AUDIT_AGGREGATE_TYPE,
                assignmentPublicId.toString(), entry.operation().name(), summary, requestPublicId, null,
                jsonMapper.writeValueAsString(entry)));
    }

    PagedResult<AuditHistoryEntry> history(UUID tenantId, UUID assignmentPublicId, int page, int size) {
        return auditReader.history(tenantId, ExaminerScoringConstants.AUDIT_AGGREGATE_TYPE,
                assignmentPublicId.toString(), page, size);
    }

    Entry readEntry(AuditHistoryEntry row) {
        return row.afterStateJson() == null ? null : jsonMapper.readValue(row.afterStateJson(), Entry.class);
    }

    /** Hex SHA-256 of the canonical command intent. */
    static String fingerprint(String canonicalIntent) {
        return ExaminerReviewVersionService.sha256Hex(canonicalIntent);
    }

    private static String key(UUID assignmentPublicId, UUID examinerPublicId, UUID requestPublicId) {
        return "examiner:" + assignmentPublicId + ":" + examinerPublicId + ":" + requestPublicId;
    }
}
