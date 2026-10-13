package com.pte.scoring.internal.service;

import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.scoring.domain.ExaminerAnswerDraft;
import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.ExaminerGradingEvent;
import com.pte.scoring.domain.ExaminerGradingIssue;
import com.pte.scoring.domain.ExaminerGradingWork;
import com.pte.scoring.domain.GradingCohort;
import com.pte.scoring.domain.GradingCohortMember;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.ScoringSessionState;
import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
import com.pte.scoring.domain.enums.GradingCohortStatus;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.dto.response.ExaminerDraftResponse;
import com.pte.scoring.dto.response.ExaminerGradingAuditMetadata;
import com.pte.scoring.dto.response.ExaminerGradingIssueResponse;
import com.pte.scoring.dto.response.ExaminerGuidelinesResponse;
import com.pte.scoring.dto.response.ExaminerHistoryItemResponse;
import com.pte.scoring.dto.response.ExaminerHistoryResponse;
import tools.jackson.databind.ObjectMapper;
import com.pte.scoring.dto.response.ExaminerWorkflowView;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ExaminerWorkflowQueryService {

    private final ExaminerGradingWorkRepository workRepository;
    private final ExaminerAnswerDraftRepository draftRepository;
    private final ExaminerGradingEventRepository eventRepository;
    private final ExaminerGradingIssueRepository issueRepository;
    private final ExaminerAttemptAssignmentRepository assignmentRepository;
    private final ExaminerAnswerScoreRepository scoreRepository;
    private final ScoringSessionStateRepository sessionStateRepository;
    private final GradingCohortRepository cohortRepository;
    private final GradingCohortMemberRepository memberRepository;
    private final ScoringAnswerRepository answerRepository;
    private final ScoreTemplateService scoreTemplateService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ExaminerWorkflowQueryService(
            ExaminerGradingWorkRepository workRepository,
            ExaminerAnswerDraftRepository draftRepository,
            ExaminerGradingEventRepository eventRepository,
            ExaminerGradingIssueRepository issueRepository,
            ExaminerAttemptAssignmentRepository assignmentRepository,
            ExaminerAnswerScoreRepository scoreRepository,
            ScoringSessionStateRepository sessionStateRepository,
            GradingCohortRepository cohortRepository,
            GradingCohortMemberRepository memberRepository,
            ScoringAnswerRepository answerRepository,
            ScoreTemplateService scoreTemplateService,
            @Autowired(required = false) ObjectMapper objectMapper) {
        this.workRepository = workRepository;
        this.draftRepository = draftRepository;
        this.eventRepository = eventRepository;
        this.issueRepository = issueRepository;
        this.assignmentRepository = assignmentRepository;
        this.scoreRepository = scoreRepository;
        this.sessionStateRepository = sessionStateRepository;
        this.cohortRepository = cohortRepository;
        this.memberRepository = memberRepository;
        this.answerRepository = answerRepository;
        this.scoreTemplateService = scoreTemplateService;
        this.objectMapper = objectMapper != null
                ? objectMapper
                : tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
    }

    public ExaminerWorkflowQueryService(
            ExaminerGradingWorkRepository workRepository,
            ExaminerAnswerDraftRepository draftRepository,
            ExaminerGradingEventRepository eventRepository,
            ExaminerGradingIssueRepository issueRepository,
            ExaminerAttemptAssignmentRepository assignmentRepository,
            ExaminerAnswerScoreRepository scoreRepository,
            ScoringSessionStateRepository sessionStateRepository,
            GradingCohortRepository cohortRepository,
            GradingCohortMemberRepository memberRepository,
            ScoringAnswerRepository answerRepository,
            ScoreTemplateService scoreTemplateService) {
        this(workRepository, draftRepository, eventRepository, issueRepository, assignmentRepository,
                scoreRepository, sessionStateRepository, cohortRepository, memberRepository, answerRepository,
                scoreTemplateService, null);
    }

    public ExaminerWorkflowView buildWorkflowView(ExaminerAttemptAssignment assignment, UUID tenantId) {
        Optional<ExaminerGradingWork> workOpt = workRepository
                .findByTenantIdAndAssignmentPublicIdAndDeletedFalse(tenantId, assignment.getPublicId());

        Optional<ScoringSessionState> sessionStateOpt = sessionStateRepository
                .findByTenantIdAndSessionPublicId(tenantId, assignment.getSessionPublicId());
        boolean isPublished = sessionStateOpt.map(s -> s.getPublicationPublicId() != null).orElse(false);

        Optional<GradingCohort> cohortOpt = cohortRepository
                .findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, assignment.getSessionPublicId());
        boolean isCohortFrozenOrCompleted = cohortOpt.map(c ->
                (c.getStatus() == GradingCohortStatus.FROZEN || c.getStatus() == GradingCohortStatus.COMPLETED)
                        && c.getMarkingMode() == GradingMarkingMode.MANUAL_EXAMINER
        ).orElse(false);

        boolean isMemberActive = true;
        if (cohortOpt.isPresent()) {
            Optional<GradingCohortMember> memberOpt = memberRepository
                    .findByCohortPublicIdAndAttemptPublicIdAndDeletedFalse(
                            cohortOpt.get().getPublicId(), assignment.getAttemptPublicId());
            isMemberActive = memberOpt.isPresent() && !memberOpt.get().isExcluded();
        }

        String blockingReason = null;
        if (isPublished) {
            blockingReason = "ALREADY_PUBLISHED";
        } else if (!isCohortFrozenOrCompleted) {
            blockingReason = "WAITING_FOR_HOST_FREEZE";
        } else if (!isMemberActive) {
            blockingReason = "EXCLUDED_FROM_COHORT";
        }

        ExaminerWorkflowStatus status;
        long workVersion = 0L;
        Instant startedAt = null;
        Instant finalizedAt = null;
        List<ExaminerDraftResponse> drafts = Collections.emptyList();

        if (workOpt.isPresent()) {
            ExaminerGradingWork work = workOpt.get();
            status = work.getStatus();
            workVersion = work.getVersion();
            startedAt = work.getStartedAt();
            finalizedAt = work.getFinalizedAt();

            List<ExaminerAnswerDraft> draftEntities = draftRepository
                    .findByTenantIdAndWorkIdAndDeletedFalse(tenantId, work.getId());
            drafts = draftEntities.stream()
                    .map(d -> new ExaminerDraftResponse(
                            d.getAnswerPublicId(),
                            d.getVersion(),
                            d.getScore(),
                            d.getComment(),
                            d.getDecisionKind(),
                            d.getExpectedReviewVersion(),
                            d.getReviewedAiVersion(),
                            d.getSavedAt()))
                    .toList();
        } else {
            // Derived legacy state
            long submittedFinals = scoreRepository.countByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicIdAndDeletedFalse(
                    tenantId, assignment.getSessionPublicId(), assignment.getAttemptPublicId(), assignment.getExaminerPublicId());
            if (submittedFinals == 0) {
                status = ExaminerWorkflowStatus.PENDING;
            } else if (submittedFinals < assignment.getEligibleAnswerCount()) {
                status = ExaminerWorkflowStatus.IN_PROGRESS;
            } else {
                status = ExaminerWorkflowStatus.COMPLETED;
            }
        }

        boolean readOnly = isPublished || status == ExaminerWorkflowStatus.COMPLETED;
        boolean canStart = !readOnly && status == ExaminerWorkflowStatus.PENDING;
        boolean canSaveDraft = !readOnly && status == ExaminerWorkflowStatus.IN_PROGRESS;
        boolean canFinalize = !readOnly && status == ExaminerWorkflowStatus.IN_PROGRESS && blockingReason == null;

        return new ExaminerWorkflowView(
                "ATOMIC_DRAFT",
                status,
                workVersion,
                startedAt,
                finalizedAt,
                readOnly,
                canStart,
                canSaveDraft,
                canFinalize,
                blockingReason,
                drafts
        );
    }

    public ExaminerHistoryResponse getHistory(UUID sessionPublicId, UUID attemptPublicId, Pageable pageable, CurrentUser user) {
        ExaminerAttemptAssignment assignment = requireAssignment(sessionPublicId, attemptPublicId, user);
        Optional<ExaminerGradingWork> workOpt = workRepository
                .findByTenantIdAndAssignmentPublicIdAndDeletedFalse(user.tenantId(), assignment.getPublicId());

        if (workOpt.isEmpty()) {
            return new ExaminerHistoryResponse(Collections.emptyList(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
        }

        Page<ExaminerGradingEvent> eventPage = eventRepository
                .findByTenantIdAndWorkId(user.tenantId(), workOpt.get().getId(), pageable);

        List<ExaminerHistoryItemResponse> items = eventPage.getContent().stream()
                .map(e -> {
                    ExaminerGradingAuditMetadata meta = parseAuditMetadata(e.getMetadata());
                    return new ExaminerHistoryItemResponse(
                            e.getPublicId(),
                            assignment.getSessionPublicId(),
                            assignment.getAttemptPublicId(),
                            e.getAnswerPublicId(),
                            e.getOperation(),
                            e.getOccurredAt(),
                            e.getWorkVersion(),
                            e.getDraftVersion(),
                            meta != null ? meta.actorPublicId() : null,
                            meta != null ? meta.actorRole() : null,
                            meta != null ? meta.assignmentPublicId() : assignment.getPublicId(),
                            meta != null ? meta.previousScore() : null,
                            meta != null ? meta.newScore() : null,
                            meta != null ? meta.rubricVersion() : null,
                            meta != null ? meta.reason() : null);
                })
                .toList();

        return new ExaminerHistoryResponse(
                items,
                eventPage.getNumber(),
                eventPage.getSize(),
                eventPage.getTotalElements(),
                eventPage.getTotalPages()
        );
    }

    public List<ExaminerGradingIssueResponse> getIssues(UUID sessionPublicId, UUID attemptPublicId, CurrentUser user) {
        ExaminerAttemptAssignment assignment = requireAssignment(sessionPublicId, attemptPublicId, user);
        Optional<ExaminerGradingWork> workOpt = workRepository
                .findByTenantIdAndAssignmentPublicIdAndDeletedFalse(user.tenantId(), assignment.getPublicId());

        if (workOpt.isEmpty()) {
            return Collections.emptyList();
        }

        List<ExaminerGradingIssue> issues = issueRepository
                .findByTenantIdAndWorkIdAndDeletedFalseOrderByReportedAtAsc(user.tenantId(), workOpt.get().getId());

        return issues.stream()
                .map(i -> new ExaminerGradingIssueResponse(
                        i.getPublicId(),
                        assignment.getSessionPublicId(),
                        assignment.getAttemptPublicId(),
                        i.getAnswerPublicId(),
                        i.getType(),
                        i.getDescription(),
                        i.getStatus(),
                        i.getReportedAt(),
                        i.getResolvedAt(),
                        i.getResolution()))
                .toList();
    }

    public ExaminerGuidelinesResponse getGuidelines(UUID sessionPublicId, UUID attemptPublicId, CurrentUser user) {
        requireAssignment(sessionPublicId, attemptPublicId, user);
        List<ScoringAnswer> answers = answerRepository
                .findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndDeletedFalse(user.tenantId(), sessionPublicId, attemptPublicId);

        List<ExaminerGuidelinesResponse.ExaminerTemplateGuidelineItem> templates = new ArrayList<>();
        List<UUID> templateIds = answers.stream()
                .map(ScoringAnswer::getScoreTemplatePublicId)
                .filter(id -> id != null)
                .distinct()
                .toList();

        for (UUID templateId : templateIds) {
            try {
                ScoreTemplateResponse template = scoreTemplateService.getByPublicId(templateId);
                if (template != null) {
                    templates.add(new ExaminerGuidelinesResponse.ExaminerTemplateGuidelineItem(templateId, template.items()));
                }
            } catch (Exception ignored) {
                // Ignore missing template lookup
            }
        }

        return new ExaminerGuidelinesResponse(
                0,
                100,
                "Scalar scoring 0-100 applies to each eligible task without criterion weights.",
                templates
        );
    }

    public ExaminerAttemptAssignment requireAssignment(UUID sessionPublicId, UUID attemptPublicId, CurrentUser user) {
        return assignmentRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicIdAndDeletedFalse(
                user.tenantId(), sessionPublicId, attemptPublicId, user.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found or unauthorized"));
    }

    private ExaminerGradingAuditMetadata parseAuditMetadata(String json) {
        if (json == null || json.isBlank() || objectMapper == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, ExaminerGradingAuditMetadata.class);
        } catch (Exception e) {
            return null;
        }
    }
}
