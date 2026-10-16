package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.ExaminerGradingCommand;
import com.pte.scoring.domain.ExaminerGradingEvent;
import com.pte.scoring.domain.ExaminerGradingIssue;
import com.pte.scoring.domain.ExaminerGradingWork;
import com.pte.scoring.domain.ScoringSessionState;
import com.pte.scoring.domain.enums.ExaminerGradingOperation;
import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
import com.pte.scoring.dto.request.ReportGradingIssueRequest;
import com.pte.scoring.dto.request.ResolveGradingIssueRequest;
import com.pte.scoring.dto.request.StartExaminerWorkRequest;
import com.pte.scoring.dto.response.ExaminerCommandReceiptResponse;
import com.pte.scoring.dto.response.ExaminerCommandResponse;
import com.pte.scoring.dto.response.ExaminerGradingIssueResponse;
import com.pte.scoring.dto.response.ExaminerWorkflowView;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.scoring.internal.repository.ExaminerGradingCommandRepository;
import com.pte.scoring.internal.repository.ExaminerGradingEventRepository;
import com.pte.scoring.internal.repository.ExaminerGradingIssueRepository;
import com.pte.scoring.internal.repository.ExaminerGradingWorkRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import com.pte.scoring.dto.response.ExaminerGradingAuditMetadata;
import com.pte.session.SessionService;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExaminerWorkflowCommandService {

    private final SessionService sessionService;
    private final ExaminerAttemptAssignmentRepository assignmentRepository;
    private final ExaminerGradingWorkRepository workRepository;
    private final ExaminerGradingEventRepository eventRepository;
    private final ExaminerGradingCommandRepository commandRepository;
    private final ExaminerGradingIssueRepository issueRepository;
    private final ScoringSessionStateRepository sessionStateRepository;
    private final ExaminerWorkflowQueryService workflowQueryService;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Autowired
    public ExaminerWorkflowCommandService(
            SessionService sessionService,
            ExaminerAttemptAssignmentRepository assignmentRepository,
            ExaminerGradingWorkRepository workRepository,
            ExaminerGradingEventRepository eventRepository,
            ExaminerGradingCommandRepository commandRepository,
            ExaminerGradingIssueRepository issueRepository,
            ScoringSessionStateRepository sessionStateRepository,
            ExaminerWorkflowQueryService workflowQueryService,
            ObjectMapper objectMapper,
            @Autowired(required = false) AuditLogService auditLogService,
            Clock clock) {
        this.sessionService = sessionService;
        this.assignmentRepository = assignmentRepository;
        this.workRepository = workRepository;
        this.eventRepository = eventRepository;
        this.commandRepository = commandRepository;
        this.issueRepository = issueRepository;
        this.sessionStateRepository = sessionStateRepository;
        this.workflowQueryService = workflowQueryService;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    public ExaminerWorkflowCommandService(
            SessionService sessionService,
            ExaminerAttemptAssignmentRepository assignmentRepository,
            ExaminerGradingWorkRepository workRepository,
            ExaminerGradingEventRepository eventRepository,
            ExaminerGradingCommandRepository commandRepository,
            ExaminerGradingIssueRepository issueRepository,
            ScoringSessionStateRepository sessionStateRepository,
            ExaminerWorkflowQueryService workflowQueryService,
            ObjectMapper objectMapper,
            Clock clock) {
        this(sessionService, assignmentRepository, workRepository, eventRepository, commandRepository,
                issueRepository, sessionStateRepository, workflowQueryService, objectMapper, null, clock);
    }

    @Transactional
    public ExaminerCommandResponse start(
            UUID sessionPublicId, UUID attemptPublicId, StartExaminerWorkRequest request, CurrentUser user) {
        sessionService.lockForScoreReviewMutation(sessionPublicId, user.tenantId());

        ExaminerAttemptAssignment assignment = requireAssignment(sessionPublicId, attemptPublicId, user);
        assertNotPublished(user.tenantId(), sessionPublicId);

        String fingerprint = computeFingerprint("START:" + user.tenantId() + ":" + assignment.getPublicId()
                + ":" + request.requestPublicId() + ":" + request.expectedWorkVersion());

        Optional<ExaminerGradingCommand> existingCmd = commandRepository
                .findByTenantIdAndExaminerPublicIdAndAssignmentPublicIdAndRequestPublicId(
                        user.tenantId(), user.userId(), assignment.getPublicId(), request.requestPublicId());

        if (existingCmd.isPresent()) {
            ExaminerGradingCommand cmd = existingCmd.get();
            if (!fingerprint.equals(cmd.getFingerprint())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key reused with different payload");
            }
            ExaminerCommandReceiptResponse receipt = deserializeReceipt(cmd.getReceiptJson());
            ExaminerWorkflowView currentView = workflowQueryService.buildWorkflowView(assignment, user.tenantId());
            return new ExaminerCommandResponse(receipt, currentView, null);
        }

        Instant now = clock.instant();
        Optional<ExaminerGradingWork> workOpt = workRepository
                .findByAssignmentWithLock(user.tenantId(), assignment.getPublicId());

        ExaminerGradingWork work;
        ExaminerGradingOperation op = ExaminerGradingOperation.START;
        if (workOpt.isEmpty()) {
            if (request.expectedWorkVersion() != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Expected work version mismatch for unstarted work: expected " + request.expectedWorkVersion() + " but was 0");
            }
            work = new ExaminerGradingWork(user.tenantId(), sessionPublicId, attemptPublicId,
                    assignment.getPublicId(), user.userId(), now);
            work = workRepository.save(work);
        } else {
            work = workOpt.get();
            if (request.expectedWorkVersion() != work.getVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Expected work version mismatch: expected "
                        + request.expectedWorkVersion() + " but was " + work.getVersion());
            }
            if (work.getStatus() == ExaminerWorkflowStatus.COMPLETED) {
                work.reopen(now);
                op = ExaminerGradingOperation.REGRADE;
            } else {
                work.start(now);
            }
            work = workRepository.save(work);
        }

        ExaminerGradingAuditMetadata auditMetadata = new ExaminerGradingAuditMetadata(
                user.userId(),
                "EXAMINER",
                assignment.getPublicId(),
                null,
                null,
                1L,
                op == ExaminerGradingOperation.REGRADE ? "Grading reopened for regrade" : "Grading started"
        );
        String metadataJson = serializeAuditMetadata(auditMetadata);

        ExaminerGradingEvent event = new ExaminerGradingEvent(
                user.tenantId(), work.getId(), null, op,
                work.getVersion(), null, metadataJson, now);
        eventRepository.save(event);

        if (auditLogService != null) {
            auditLogService.record(user, "EXAMINER_WORK", assignment.getPublicId().toString(), op.name(),
                    op == ExaminerGradingOperation.REGRADE ? "Grading reopened for regrade on attempt " + attemptPublicId : "Grading started on attempt " + attemptPublicId);
        }

        ExaminerCommandReceiptResponse receipt = new ExaminerCommandReceiptResponse(
                request.requestPublicId(), op, now,
                work.getVersion(), null, null, Collections.emptyList());

        String receiptJson = serializeReceipt(receipt);
        ExaminerGradingCommand cmd = new ExaminerGradingCommand(
                user.tenantId(), user.userId(), assignment.getPublicId(), request.requestPublicId(),
                "START", fingerprint, receiptJson, now);
        commandRepository.save(cmd);

        ExaminerWorkflowView currentView = workflowQueryService.buildWorkflowView(assignment, user.tenantId());
        return new ExaminerCommandResponse(receipt, currentView, null);
    }

    @Transactional(readOnly = true)
    public ExaminerCommandResponse getCommand(
            UUID sessionPublicId, UUID attemptPublicId, UUID requestPublicId, CurrentUser user) {
        ExaminerAttemptAssignment assignment = requireAssignment(sessionPublicId, attemptPublicId, user);

        ExaminerGradingCommand cmd = commandRepository
                .findByTenantIdAndExaminerPublicIdAndAssignmentPublicIdAndRequestPublicId(
                        user.tenantId(), user.userId(), assignment.getPublicId(), requestPublicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Command receipt not found"));

        ExaminerCommandReceiptResponse receipt = deserializeReceipt(cmd.getReceiptJson());
        ExaminerWorkflowView currentView = workflowQueryService.buildWorkflowView(assignment, user.tenantId());
        return new ExaminerCommandResponse(receipt, currentView, null);
    }

    @Transactional
    public ExaminerCommandResponse reportIssue(
            UUID sessionPublicId, UUID attemptPublicId, ReportGradingIssueRequest request, CurrentUser user) {
        sessionService.lockForScoreReviewMutation(sessionPublicId, user.tenantId());

        ExaminerAttemptAssignment assignment = requireAssignment(sessionPublicId, attemptPublicId, user);
        assertNotPublished(user.tenantId(), sessionPublicId);

        ExaminerGradingWork work = workRepository
                .findByAssignmentWithLock(user.tenantId(), assignment.getPublicId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work must be started before reporting issues"));

        if (work.getStatus() == ExaminerWorkflowStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot report issues on finalized work");
        }
        if (request.expectedWorkVersion() != work.getVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Expected work version mismatch");
        }

        String fingerprint = computeFingerprint("REPORT_ISSUE:" + user.tenantId() + ":" + assignment.getPublicId()
                + ":" + request.requestPublicId() + ":" + request.type() + ":" + request.description().trim());

        Optional<ExaminerGradingCommand> existingCmd = commandRepository
                .findByTenantIdAndExaminerPublicIdAndAssignmentPublicIdAndRequestPublicId(
                        user.tenantId(), user.userId(), assignment.getPublicId(), request.requestPublicId());

        if (existingCmd.isPresent()) {
            ExaminerGradingCommand cmd = existingCmd.get();
            if (!fingerprint.equals(cmd.getFingerprint())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key reused with different payload");
            }
            ExaminerCommandReceiptResponse receipt = deserializeReceipt(cmd.getReceiptJson());
            ExaminerWorkflowView currentView = workflowQueryService.buildWorkflowView(assignment, user.tenantId());
            return new ExaminerCommandResponse(receipt, currentView, null);
        }

        Instant now = clock.instant();
        ExaminerGradingIssue issue = new ExaminerGradingIssue(
                user.tenantId(), work.getId(), request.answerPublicId(), request.type(),
                request.description().trim(), user.userId(), now);
        issueRepository.save(issue);

        ExaminerGradingAuditMetadata reportAuditMetadata = new ExaminerGradingAuditMetadata(
                user.userId(),
                "EXAMINER",
                assignment.getPublicId(),
                null,
                null,
                1L,
                request.type() + ": " + request.description().trim()
        );
        String reportMetadataJson = serializeAuditMetadata(reportAuditMetadata);

        ExaminerGradingEvent event = new ExaminerGradingEvent(
                user.tenantId(), work.getId(), request.answerPublicId(), ExaminerGradingOperation.REPORT_ISSUE,
                work.getVersion(), null, reportMetadataJson, now);
        eventRepository.save(event);

        if (auditLogService != null) {
            auditLogService.record(user, "EXAMINER_WORK", assignment.getPublicId().toString(), "REPORT_ISSUE",
                    "Reported issue " + request.type() + " on attempt " + attemptPublicId);
        }

        ExaminerCommandReceiptResponse receipt = new ExaminerCommandReceiptResponse(
                request.requestPublicId(), ExaminerGradingOperation.REPORT_ISSUE, now,
                work.getVersion(), request.answerPublicId(), null, Collections.emptyList());

        String receiptJson = serializeReceipt(receipt);
        ExaminerGradingCommand cmd = new ExaminerGradingCommand(
                user.tenantId(), user.userId(), assignment.getPublicId(), request.requestPublicId(),
                "REPORT_ISSUE", fingerprint, receiptJson, now);
        commandRepository.save(cmd);

        ExaminerWorkflowView currentView = workflowQueryService.buildWorkflowView(assignment, user.tenantId());
        return new ExaminerCommandResponse(receipt, currentView, null);
    }

    @Transactional
    public ExaminerGradingIssueResponse resolveIssue(
            UUID issuePublicId, ResolveGradingIssueRequest request, CurrentUser user) {
        ExaminerGradingIssue issue = issueRepository
                .findByTenantIdAndPublicIdAndDeletedFalse(user.tenantId(), issuePublicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Grading issue not found"));

        ExaminerGradingWork work = workRepository.findById(issue.getWorkId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Associated work not found"));

        sessionService.lockForScoreReviewMutation(work.getSessionPublicId(), user.tenantId());

        Instant now = clock.instant();
        issue.resolve(user.userId(), request.resolution().trim(), now);
        issueRepository.save(issue);

        ExaminerGradingAuditMetadata resolveAuditMetadata = new ExaminerGradingAuditMetadata(
                user.userId(),
                "EXAMINER",
                work.getAssignmentPublicId(),
                null,
                null,
                1L,
                "Resolved issue: " + request.resolution().trim()
        );
        String resolveMetadataJson = serializeAuditMetadata(resolveAuditMetadata);

        ExaminerGradingEvent event = new ExaminerGradingEvent(
                user.tenantId(), work.getId(), issue.getAnswerPublicId(), ExaminerGradingOperation.RESOLVE_ISSUE,
                work.getVersion(), null, resolveMetadataJson, now);
        eventRepository.save(event);

        if (auditLogService != null) {
            auditLogService.record(user, "EXAMINER_WORK", work.getAssignmentPublicId().toString(), "RESOLVE_ISSUE",
                    "Resolved issue " + issuePublicId + " on attempt " + work.getAttemptPublicId());
        }

        return new ExaminerGradingIssueResponse(
                issue.getPublicId(),
                work.getSessionPublicId(),
                work.getAttemptPublicId(),
                issue.getAnswerPublicId(),
                issue.getType(),
                issue.getDescription(),
                issue.getStatus(),
                issue.getReportedAt(),
                issue.getResolvedAt(),
                issue.getResolution()
        );
    }

    private ExaminerAttemptAssignment requireAssignment(UUID sessionPublicId, UUID attemptPublicId, CurrentUser user) {
        return assignmentRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicIdAndDeletedFalse(
                user.tenantId(), sessionPublicId, attemptPublicId, user.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found or unauthorized"));
    }

    private void assertNotPublished(UUID tenantId, UUID sessionPublicId) {
        Optional<ScoringSessionState> sessionStateOpt = sessionStateRepository
                .findByTenantIdAndSessionPublicId(tenantId, sessionPublicId);
        if (sessionStateOpt.isPresent() && sessionStateOpt.get().getPublicationPublicId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session scores already published");
        }
    }

    private String computeFingerprint(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 calculation failed", e);
        }
    }

    private String serializeReceipt(ExaminerCommandReceiptResponse receipt) {
        try {
            return objectMapper.writeValueAsString(receipt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize receipt JSON", e);
        }
    }

    private ExaminerCommandReceiptResponse deserializeReceipt(String receiptJson) {
        try {
            return objectMapper.readValue(receiptJson, ExaminerCommandReceiptResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize receipt JSON", e);
        }
    }

    private String serializeAuditMetadata(ExaminerGradingAuditMetadata metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            return null;
        }
    }
}
