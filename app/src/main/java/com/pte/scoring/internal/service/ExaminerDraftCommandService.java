package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ExaminerAnswerDraft;
import com.pte.scoring.domain.ExaminerAnswerScore;
import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.ExaminerGradingCommand;
import com.pte.scoring.domain.ExaminerGradingEvent;
import com.pte.scoring.domain.ExaminerGradingIssue;
import com.pte.scoring.domain.ExaminerGradingWork;
import com.pte.scoring.domain.GradingCohort;
import com.pte.scoring.domain.GradingCohortMember;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.ScoringSessionState;
import com.pte.scoring.domain.enums.ExaminerAiAvailability;
import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import com.pte.scoring.domain.enums.ExaminerGradingIssueStatus;
import com.pte.scoring.domain.enums.ExaminerGradingOperation;
import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
import com.pte.scoring.domain.enums.GradingCohortStatus;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.dto.request.FinalizeExaminerWorkRequest;
import com.pte.scoring.dto.request.SaveExaminerDraftRequest;
import com.pte.scoring.dto.response.ExaminerAiReferenceResponse;
import com.pte.scoring.dto.response.ExaminerCommandReceiptResponse;
import com.pte.scoring.dto.response.ExaminerCommandResponse;
import com.pte.scoring.dto.response.ExaminerDraftResponse;
import com.pte.scoring.dto.response.ExaminerWorkflowView;
import com.pte.scoring.internal.repository.ExaminerAnswerDraftRepository;
import com.pte.scoring.internal.repository.ExaminerAnswerScoreRepository;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.scoring.internal.repository.ExaminerGradingCommandRepository;
import com.pte.scoring.internal.repository.ExaminerGradingEventRepository;
import com.pte.scoring.internal.repository.ExaminerGradingIssueRepository;
import com.pte.scoring.internal.repository.ExaminerGradingWorkRepository;
import com.pte.scoring.internal.repository.GradingCohortMemberRepository;
import com.pte.scoring.internal.repository.GradingCohortRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import com.pte.session.SessionService;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ExaminerDraftCommandService {

    private final SessionService sessionService;
    private final ExaminerAttemptAssignmentRepository assignmentRepository;
    private final ExaminerGradingWorkRepository workRepository;
    private final ExaminerAnswerDraftRepository draftRepository;
    private final ExaminerGradingEventRepository eventRepository;
    private final ExaminerGradingCommandRepository commandRepository;
    private final ExaminerGradingIssueRepository issueRepository;
    private final ExaminerAnswerScoreRepository scoreRepository;
    private final ScoringAnswerRepository answerRepository;
    private final ScoringSessionStateRepository sessionStateRepository;
    private final GradingCohortRepository cohortRepository;
    private final GradingCohortMemberRepository memberRepository;
    private final ScoringEligibilityQueryService eligibilityQueryService;
    private final ExaminerWorkflowQueryService workflowQueryService;
    private final ExaminerReviewVersionService examinerReviewVersionService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ExaminerDraftCommandService(
            SessionService sessionService,
            ExaminerAttemptAssignmentRepository assignmentRepository,
            ExaminerGradingWorkRepository workRepository,
            ExaminerAnswerDraftRepository draftRepository,
            ExaminerGradingEventRepository eventRepository,
            ExaminerGradingCommandRepository commandRepository,
            ExaminerGradingIssueRepository issueRepository,
            ExaminerAnswerScoreRepository scoreRepository,
            ScoringAnswerRepository answerRepository,
            ScoringSessionStateRepository sessionStateRepository,
            GradingCohortRepository cohortRepository,
            GradingCohortMemberRepository memberRepository,
            ScoringEligibilityQueryService eligibilityQueryService,
            ExaminerWorkflowQueryService workflowQueryService,
            ExaminerReviewVersionService examinerReviewVersionService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.sessionService = sessionService;
        this.assignmentRepository = assignmentRepository;
        this.workRepository = workRepository;
        this.draftRepository = draftRepository;
        this.eventRepository = eventRepository;
        this.commandRepository = commandRepository;
        this.issueRepository = issueRepository;
        this.scoreRepository = scoreRepository;
        this.answerRepository = answerRepository;
        this.sessionStateRepository = sessionStateRepository;
        this.cohortRepository = cohortRepository;
        this.memberRepository = memberRepository;
        this.eligibilityQueryService = eligibilityQueryService;
        this.workflowQueryService = workflowQueryService;
        this.examinerReviewVersionService = examinerReviewVersionService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ExaminerCommandResponse saveDraft(
            UUID sessionPublicId, UUID attemptPublicId, UUID answerPublicId,
            SaveExaminerDraftRequest request, CurrentUser user) {
        sessionService.lockForScoreReviewMutation(sessionPublicId, user.tenantId());

        ExaminerAttemptAssignment assignment = requireAssignment(sessionPublicId, attemptPublicId, user);
        assertNotPublished(user.tenantId(), sessionPublicId);

        ExaminerGradingWork work = workRepository
                .findByAssignmentWithLock(user.tenantId(), assignment.getPublicId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work must be started before saving drafts"));

        if (work.getStatus() == ExaminerWorkflowStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot save drafts on finalized work");
        }
        if (request.expectedWorkVersion() != work.getVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Expected work version mismatch");
        }

        boolean answerBelongs = answerRepository.existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndAnswerPublicIdAndDeletedFalse(
                user.tenantId(), sessionPublicId, attemptPublicId, answerPublicId);
        if (!answerBelongs) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Answer not found in assigned attempt");
        }

        if (request.decisionKind() == ExaminerDecisionKind.CONFIRM_AI) {
            ScoringAnswer scoringAnswer = answerRepository.findByAnswerPublicId(answerPublicId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Answer not found"));
            ExaminerAiReferenceResponse aiRef = examinerReviewVersionService.buildAiReference(scoringAnswer);
            if (aiRef.availability() != ExaminerAiAvailability.AVAILABLE || aiRef.score() == null
                    || !Objects.equals(aiRef.version(), request.reviewedAiVersion())
                    || !Objects.equals(aiRef.score(), request.score())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CONFIRM_AI score or version does not match active AI score");
            }
        }

        String fingerprint = computeFingerprint("SAVE_DRAFT:" + user.tenantId() + ":" + assignment.getPublicId()
                + ":" + answerPublicId + ":" + request.requestPublicId() + ":" + request.expectedDraftVersion()
                + ":" + request.score() + ":" + (request.comment() != null ? request.comment().trim() : "")
                + ":" + request.decisionKind() + ":" + request.expectedReviewVersion() + ":" + request.reviewedAiVersion());

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
            ExaminerDraftResponse draftResponse = draftRepository
                    .findByTenantIdAndWorkIdAndAnswerPublicIdAndDeletedFalse(user.tenantId(), work.getId(), answerPublicId)
                    .map(d -> new ExaminerDraftResponse(d.getAnswerPublicId(), d.getVersion(), d.getScore(), d.getComment(), d.getDecisionKind(), d.getExpectedReviewVersion(), d.getReviewedAiVersion(), d.getSavedAt()))
                    .orElse(null);
            return new ExaminerCommandResponse(receipt, currentView, draftResponse);
        }

        Instant now = clock.instant();
        Optional<ExaminerAnswerDraft> draftOpt = draftRepository
                .findByWorkAndAnswerWithLock(user.tenantId(), work.getId(), answerPublicId);

        ExaminerAnswerDraft draft;
        if (draftOpt.isEmpty()) {
            if (request.expectedDraftVersion() != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Expected draft version mismatch for unpersisted draft");
            }
            draft = new ExaminerAnswerDraft(
                    user.tenantId(), work.getId(), answerPublicId, request.score(),
                    request.comment() != null ? request.comment().trim() : null,
                    request.decisionKind(), request.expectedReviewVersion(), request.reviewedAiVersion(), now);
            draft = draftRepository.save(draft);
        } else {
            draft = draftOpt.get();
            if (request.expectedDraftVersion() != draft.getVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Expected draft version mismatch: expected "
                        + request.expectedDraftVersion() + " but was " + draft.getVersion());
            }
            draft.update(request.score(), request.comment() != null ? request.comment().trim() : null,
                    request.decisionKind(), request.expectedReviewVersion(), request.reviewedAiVersion(), now);
            draft = draftRepository.save(draft);
        }

        ExaminerGradingEvent event = new ExaminerGradingEvent(
                user.tenantId(), work.getId(), answerPublicId, ExaminerGradingOperation.SAVE_DRAFT,
                work.getVersion(), draft.getVersion(), null, now);
        eventRepository.save(event);

        ExaminerCommandReceiptResponse receipt = new ExaminerCommandReceiptResponse(
                request.requestPublicId(), ExaminerGradingOperation.SAVE_DRAFT, now,
                work.getVersion(), answerPublicId, draft.getVersion(), Collections.emptyList());

        String receiptJson = serializeReceipt(receipt);
        ExaminerGradingCommand cmd = new ExaminerGradingCommand(
                user.tenantId(), user.userId(), assignment.getPublicId(), request.requestPublicId(),
                "SAVE_DRAFT", fingerprint, receiptJson, now);
        commandRepository.save(cmd);

        ExaminerWorkflowView currentView = workflowQueryService.buildWorkflowView(assignment, user.tenantId());
        ExaminerDraftResponse draftResponse = new ExaminerDraftResponse(
                draft.getAnswerPublicId(), draft.getVersion(), draft.getScore(), draft.getComment(),
                draft.getDecisionKind(), draft.getExpectedReviewVersion(), draft.getReviewedAiVersion(), draft.getSavedAt());

        return new ExaminerCommandResponse(receipt, currentView, draftResponse);
    }

    @Transactional
    public ExaminerCommandResponse finalizeWork(
            UUID sessionPublicId, UUID attemptPublicId, FinalizeExaminerWorkRequest request, CurrentUser user) {
        sessionService.lockForScoreReviewMutation(sessionPublicId, user.tenantId());

        ExaminerAttemptAssignment assignment = requireAssignment(sessionPublicId, attemptPublicId, user);

        String manifestString = request.answers().stream()
                .sorted(Comparator.comparing(FinalizeExaminerWorkRequest.FinalizeAnswerItemRequest::answerPublicId))
                .map(a -> a.answerPublicId() + ":" + a.expectedDraftVersion())
                .collect(Collectors.joining(";"));

        String fingerprint = computeFingerprint("FINALIZE:" + user.tenantId() + ":" + assignment.getPublicId()
                + ":" + request.requestPublicId() + ":" + request.expectedWorkVersion() + ":" + manifestString);

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

        assertNotPublished(user.tenantId(), sessionPublicId);

        GradingCohort cohort = cohortRepository
                .findByTenantIdAndSessionPublicIdAndDeletedFalse(user.tenantId(), sessionPublicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Cohort not found"));

        if (cohort.getMarkingMode() != GradingMarkingMode.MANUAL_EXAMINER
                || (cohort.getStatus() != GradingCohortStatus.FROZEN && cohort.getStatus() != GradingCohortStatus.COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cohort is not in FROZEN or COMPLETED manual examiner mode");
        }

        GradingCohortMember member = memberRepository
                .findByCohortPublicIdAndAttemptPublicIdAndDeletedFalse(cohort.getPublicId(), attemptPublicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Attempt is not in grading cohort"));
        if (member.isExcluded()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Attempt is excluded from grading cohort");
        }

        ExaminerGradingWork work = workRepository
                .findByAssignmentWithLock(user.tenantId(), assignment.getPublicId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Work must be started before finalization"));

        if (work.getStatus() == ExaminerWorkflowStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Work is already finalized");
        }
        if (request.expectedWorkVersion() != work.getVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Expected work version mismatch");
        }

        List<ExaminerGradingIssue> openIssues = issueRepository
                .findByTenantIdAndWorkIdAndDeletedFalseOrderByReportedAtAsc(user.tenantId(), work.getId())
                .stream()
                .filter(i -> i.getStatus() == ExaminerGradingIssueStatus.OPEN)
                .toList();
        if (!openIssues.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Unresolved blocking issues exist");
        }

        List<ScoringAnswer> eligibleAnswers = answerRepository
                .findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionPublicId, user.tenantId(), List.of(attemptPublicId))
                .stream()
                .filter(eligibilityQueryService::isAiEligible)
                .toList();

        if (eligibleAnswers.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Eligible answer pool is empty");
        }

        Set<UUID> eligibleIds = eligibleAnswers.stream().map(ScoringAnswer::getAnswerPublicId).collect(Collectors.toSet());
        Set<UUID> requestIds = request.answers().stream().map(FinalizeExaminerWorkRequest.FinalizeAnswerItemRequest::answerPublicId).collect(Collectors.toSet());

        if (!eligibleIds.equals(requestIds)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Finalize manifest does not match eligible answer pool exactly");
        }

        Map<UUID, ExaminerAnswerDraft> draftsByAnswer = draftRepository
                .findByTenantIdAndWorkIdAndDeletedFalse(user.tenantId(), work.getId())
                .stream()
                .collect(Collectors.toMap(ExaminerAnswerDraft::getAnswerPublicId, Function.identity()));

        Map<UUID, ExaminerAnswerScore> existingScores = scoreRepository
                .findByTenantIdAndSessionPublicIdAndAttemptPublicId(user.tenantId(), sessionPublicId, attemptPublicId)
                .stream()
                .collect(Collectors.toMap(ExaminerAnswerScore::getAnswerPublicId, Function.identity()));

        Map<UUID, ScoringAnswer> answersById = eligibleAnswers.stream()
                .collect(Collectors.toMap(ScoringAnswer::getAnswerPublicId, Function.identity()));

        Instant now = clock.instant();
        List<ExaminerCommandReceiptResponse.ChildRequestReceipt> childReceipts = new ArrayList<>();

        for (FinalizeExaminerWorkRequest.FinalizeAnswerItemRequest item : request.answers()) {
            if (existingScores.containsKey(item.answerPublicId())) {
                // Preserved legacy final score
                continue;
            }

            ExaminerAnswerDraft draft = draftsByAnswer.get(item.answerPublicId());
            if (draft == null || draft.getScore() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing complete draft for answer " + item.answerPublicId());
            }
            if (item.expectedDraftVersion() != draft.getVersion()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Draft version mismatch for answer " + item.answerPublicId());
            }

            UUID childUUID = UUID.nameUUIDFromBytes(("examiner-finalize-v1:" + request.requestPublicId() + ":" + item.answerPublicId()).getBytes(StandardCharsets.UTF_8));
            String childFingerprint = computeFingerprint("SCORE:" + user.tenantId() + ":" + item.answerPublicId() + ":" + childUUID + ":" + draft.getScore());

            ExaminerDecisionKind decisionKind = draft.getDecisionKind() != null ? draft.getDecisionKind() : ExaminerDecisionKind.MANUAL;
            Integer reviewedAiScore = null;
            if (decisionKind == ExaminerDecisionKind.CONFIRM_AI) {
                ScoringAnswer answerEntity = answersById.get(item.answerPublicId());
                ExaminerAiReferenceResponse aiRef = examinerReviewVersionService.buildAiReference(answerEntity);
                if (aiRef.availability() != ExaminerAiAvailability.AVAILABLE || aiRef.score() == null
                        || !Objects.equals(aiRef.version(), draft.getReviewedAiVersion())
                        || !Objects.equals(aiRef.score(), draft.getScore())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "CONFIRM_AI score or version does not match active AI score for answer " + item.answerPublicId());
                }
                reviewedAiScore = aiRef.score();
            }

            ExaminerAnswerScore score = new ExaminerAnswerScore(
                    item.answerPublicId(),
                    attemptPublicId,
                    sessionPublicId,
                    user.tenantId(),
                    user.userId(),
                    draft.getScore(),
                    now,
                    decisionKind,
                    reviewedAiScore,
                    draft.getReviewedAiVersion(),
                    draft.getExpectedReviewVersion(),
                    childUUID,
                    childFingerprint
            );
            scoreRepository.save(score);
            childReceipts.add(new ExaminerCommandReceiptResponse.ChildRequestReceipt(item.answerPublicId(), childUUID));
        }

        work.finalizeGrading(now);
        workRepository.save(work);

        ExaminerGradingEvent event = new ExaminerGradingEvent(
                user.tenantId(), work.getId(), null, ExaminerGradingOperation.FINALIZE,
                work.getVersion(), null, null, now);
        eventRepository.save(event);

        ExaminerCommandReceiptResponse receipt = new ExaminerCommandReceiptResponse(
                request.requestPublicId(), ExaminerGradingOperation.FINALIZE, now,
                work.getVersion(), null, null, childReceipts);

        String receiptJson = serializeReceipt(receipt);
        ExaminerGradingCommand cmd = new ExaminerGradingCommand(
                user.tenantId(), user.userId(), assignment.getPublicId(), request.requestPublicId(),
                "FINALIZE", fingerprint, receiptJson, now);
        commandRepository.save(cmd);

        ExaminerWorkflowView currentView = workflowQueryService.buildWorkflowView(assignment, user.tenantId());
        return new ExaminerCommandResponse(receipt, currentView, null);
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
}
