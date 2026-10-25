package com.pte.scoring.internal.service.examiner;

import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.GradingIssue;
import com.pte.scoring.domain.enums.ExaminerGradingOperation;
import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
import com.pte.scoring.dto.request.ReportGradingIssueRequest;
import com.pte.scoring.dto.request.ResolveGradingIssueRequest;
import com.pte.scoring.dto.request.StartExaminerWorkRequest;
import com.pte.scoring.dto.response.ExaminerCommandReceiptResponse;
import com.pte.scoring.dto.response.ExaminerCommandResponse;
import com.pte.scoring.dto.response.ExaminerGradingIssueResponse;
import com.pte.scoring.dto.response.ExaminerWorkflowView;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.GradingIssueRepository;
import com.pte.scoring.dto.response.ExaminerGradingAuditMetadata;
import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExaminerWorkflowCommandService {

    private final SessionService sessionService;
    private final GradingAssignmentRepository assignmentRepository;
    private final GradingIssueRepository issueRepository;
    private final SessionGradingGateway gateway;
    private final ExaminerWorkflowQueryService workflowQueryService;
    private final ExaminerWorkRecorder recorder;
    private final Clock clock;

    public ExaminerWorkflowCommandService(
            SessionService sessionService,
            GradingAssignmentRepository assignmentRepository,
            GradingIssueRepository issueRepository,
            SessionGradingGateway gateway,
            ExaminerWorkflowQueryService workflowQueryService,
            ExaminerWorkRecorder recorder,
            Clock clock) {
        this.sessionService = sessionService;
        this.assignmentRepository = assignmentRepository;
        this.issueRepository = issueRepository;
        this.gateway = gateway;
        this.workflowQueryService = workflowQueryService;
        this.recorder = recorder;
        this.clock = clock;
    }

    @Transactional
    public ExaminerCommandResponse start(
            UUID sessionPublicId, UUID attemptPublicId, StartExaminerWorkRequest request, CurrentUser user) {
        sessionService.lockForScoreReviewMutation(sessionPublicId, user.tenantId());

        GradingAssignment assignment = requireAssignment(sessionPublicId, attemptPublicId, user);
        assertNotPublished(user.tenantId(), sessionPublicId);

        String fingerprint = ExaminerWorkRecorder.fingerprint(ExaminerScoringConstants.FINGERPRINT_START + user.tenantId() + ":" + assignment.getPublicId()
                + ":" + request.requestPublicId() + ":" + request.expectedWorkVersion());

        Instant now = clock.instant();
        Optional<ExaminerCommandReceiptResponse> replay = recorder.replayOrBegin(user, assignment.getPublicId(),
                request.requestPublicId(), fingerprint, now);
        if (replay.isPresent()) {
            return new ExaminerCommandResponse(replay.get(),
                    workflowQueryService.buildWorkflowView(assignment, user.tenantId()), null);
        }

        GradingAssignment work = lock(assignment);
        ExaminerGradingOperation op = ExaminerGradingOperation.START;
        if (!work.isStarted()) {
            if (request.expectedWorkVersion() != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.UNSTARTED_VERSION_FORMAT
                        .formatted(request.expectedWorkVersion()));
            }
            work.start(now);
        } else {
            if (request.expectedWorkVersion() != work.getWorkVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.WORK_VERSION_FORMAT
                        .formatted(request.expectedWorkVersion(), work.getWorkVersion()));
            }
            if (work.getStatus() == ExaminerWorkflowStatus.COMPLETED) {
                work.reopen(now);
                op = ExaminerGradingOperation.REGRADE;
            } else {
                work.start(now);
            }
        }
        work = assignmentRepository.save(work);

        ExaminerCommandReceiptResponse receipt = new ExaminerCommandReceiptResponse(
                request.requestPublicId(), op, now,
                work.getWorkVersion(), null, null, Collections.emptyList());
        recorder.record(user, assignment.getPublicId(), request.requestPublicId(),
                op == ExaminerGradingOperation.REGRADE ? ExaminerScoringConstants.AUDIT_REGRADE_PREFIX + attemptPublicId
                        : ExaminerScoringConstants.AUDIT_START_PREFIX + attemptPublicId,
                new ExaminerWorkRecorder.Entry(null, op, work.getWorkVersion(), null, metadata(user, assignment,
                        op == ExaminerGradingOperation.REGRADE ? ExaminerScoringConstants.AUDIT_REGRADE : ExaminerScoringConstants.AUDIT_START)));
        recorder.complete(user, assignment.getPublicId(), request.requestPublicId(), receipt);

        ExaminerWorkflowView currentView = workflowQueryService.buildWorkflowView(assignment, user.tenantId());
        return new ExaminerCommandResponse(receipt, currentView, null);
    }

    @Transactional(readOnly = true)
    public ExaminerCommandResponse getCommand(
            UUID sessionPublicId, UUID attemptPublicId, UUID requestPublicId, CurrentUser user) {
        GradingAssignment assignment = requireAssignment(sessionPublicId, attemptPublicId, user);

        ExaminerCommandReceiptResponse receipt = recorder
                .findReceipt(user, assignment.getPublicId(), requestPublicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ExaminerScoringConstants.COMMAND_RECEIPT_NOT_FOUND));
        ExaminerWorkflowView currentView = workflowQueryService.buildWorkflowView(assignment, user.tenantId());
        return new ExaminerCommandResponse(receipt, currentView, null);
    }

    @Transactional
    public ExaminerCommandResponse reportIssue(
            UUID sessionPublicId, UUID attemptPublicId, ReportGradingIssueRequest request, CurrentUser user) {
        sessionService.lockForScoreReviewMutation(sessionPublicId, user.tenantId());

        GradingAssignment assignment = requireAssignment(sessionPublicId, attemptPublicId, user);
        assertNotPublished(user.tenantId(), sessionPublicId);

        GradingAssignment work = lock(assignment);
        if (!work.isStarted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ExaminerScoringConstants.WORK_NOT_STARTED_FOR_ISSUES);
        }
        if (work.getStatus() == ExaminerWorkflowStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.WORK_FINALIZED_NO_ISSUES);
        }
        if (request.expectedWorkVersion() != work.getWorkVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.WORK_VERSION_MISMATCH);
        }

        String fingerprint = ExaminerWorkRecorder.fingerprint(ExaminerScoringConstants.FINGERPRINT_REPORT_ISSUE + user.tenantId() + ":" + assignment.getPublicId()
                + ":" + request.requestPublicId() + ":" + request.type() + ":" + request.description().trim());

        Instant now = clock.instant();
        Optional<ExaminerCommandReceiptResponse> replay = recorder.replayOrBegin(user, assignment.getPublicId(),
                request.requestPublicId(), fingerprint, now);
        if (replay.isPresent()) {
            return new ExaminerCommandResponse(replay.get(),
                    workflowQueryService.buildWorkflowView(assignment, user.tenantId()), null);
        }

        GradingIssue issue = new GradingIssue(
                user.tenantId(), work.getId(), request.answerPublicId(), request.type(),
                request.description().trim(), user.userId(), now);
        issueRepository.save(issue);

        ExaminerCommandReceiptResponse receipt = new ExaminerCommandReceiptResponse(
                request.requestPublicId(), ExaminerGradingOperation.REPORT_ISSUE, now,
                work.getWorkVersion(), request.answerPublicId(), null, Collections.emptyList());
        recorder.record(user, assignment.getPublicId(), request.requestPublicId(),
                ExaminerScoringConstants.AUDIT_ISSUE_REPORTED_PREFIX + request.type() + ExaminerScoringConstants.ON_ATTEMPT + attemptPublicId,
                new ExaminerWorkRecorder.Entry(request.answerPublicId(), ExaminerGradingOperation.REPORT_ISSUE,
                        work.getWorkVersion(), null,
                        metadata(user, assignment, ExaminerScoringConstants.AUDIT_ISSUE_REASON_FORMAT.formatted(request.type(), request.description().trim()))));
        recorder.complete(user, assignment.getPublicId(), request.requestPublicId(), receipt);

        ExaminerWorkflowView currentView = workflowQueryService.buildWorkflowView(assignment, user.tenantId());
        return new ExaminerCommandResponse(receipt, currentView, null);
    }

    @Transactional
    public ExaminerGradingIssueResponse resolveIssue(
            UUID issuePublicId, ResolveGradingIssueRequest request, CurrentUser user) {
        GradingIssue issue = issueRepository
                .findByTenantIdAndPublicIdAndDeletedFalse(user.tenantId(), issuePublicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ExaminerScoringConstants.GRADING_ISSUE_NOT_FOUND));

        GradingAssignment work = assignmentRepository.findById(issue.getAssignmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        ExaminerScoringConstants.ASSIGNMENT_OF_ISSUE_NOT_FOUND));

        sessionService.lockForScoreReviewMutation(work.getSessionPublicId(), user.tenantId());

        Instant now = clock.instant();
        issue.resolve(user.userId(), request.resolution().trim(), now);
        issueRepository.save(issue);

        recorder.record(user, work.getPublicId(), null,
                ExaminerScoringConstants.AUDIT_RESOLVED_ISSUE_FORMAT.formatted(issuePublicId, work.getAttemptPublicId()),
                new ExaminerWorkRecorder.Entry(issue.getAnswerPublicId(), ExaminerGradingOperation.RESOLVE_ISSUE,
                        work.getWorkVersion(), null, new ExaminerGradingAuditMetadata(user.userId(), ExaminerScoringConstants.EXAMINER_ROLE,
                                work.getPublicId(), null, null, 1L,
                                ExaminerScoringConstants.AUDIT_RESOLUTION_PREFIX + request.resolution().trim())));

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

    private GradingAssignment requireAssignment(UUID sessionPublicId, UUID attemptPublicId, CurrentUser user) {
        return assignmentRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicIdAndDeletedFalse(
                user.tenantId(), sessionPublicId, attemptPublicId, user.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ExaminerScoringConstants.ASSIGNMENT_NOT_FOUND));
    }

    private GradingAssignment lock(GradingAssignment assignment) {
        return assignmentRepository.findForUpdate(assignment.getTenantId(), assignment.getPublicId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        ExaminerScoringConstants.ASSIGNMENT_NOT_FOUND));
    }

    private void assertNotPublished(UUID tenantId, UUID sessionPublicId) {
        if (gateway.isPublished(tenantId, sessionPublicId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ExaminerScoringConstants.SESSION_SCORES_PUBLISHED);
        }
    }

    private ExaminerGradingAuditMetadata metadata(CurrentUser user, GradingAssignment assignment,
            String reason) {
        return new ExaminerGradingAuditMetadata(user.userId(), ExaminerScoringConstants.EXAMINER_ROLE, assignment.getPublicId(), null, null,
                1L, reason);
    }
}
