package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.identity.IdentityService;
import com.pte.identity.dto.response.ExaminerIdentityView;
import com.pte.identity.dto.response.StudentDisplayNameView;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.dto.request.CommitExaminerAssignmentRequest;
import com.pte.scoring.dto.response.ExaminerAssignmentLoadResponse;
import com.pte.scoring.dto.response.ExaminerAssignmentOverviewResponse;
import com.pte.scoring.dto.response.ExaminerAssignmentRosterResponse;
import com.pte.scoring.internal.constant.ExaminerAssignmentConstants;
import com.pte.scoring.internal.exception.InvalidExaminerAssignmentException;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The host's examiner assignment of a closed session: one table, attempt to examiner, committed in one request that
 * covers every eligible submitted attempt. Committing creates a grading assignment per attempt and freezes the
 * session for manual marking. There is no preview or second step, and a committed table is never edited.
 */
@Service
public class ExaminerAssignmentService {

    private final SessionService sessionService;
    private final AttemptService attemptService;
    private final ScoringEligibilityQueryService eligibilityQueryService;
    private final IdentityService identityService;
    private final GradingAssignmentRepository assignmentRepository;
    private final ScorePublicationLockService publicationLockService;
    private final SessionGradingGateway gateway;
    private final GradingCohortService cohortService;

    public ExaminerAssignmentService(SessionService sessionService, AttemptService attemptService,
            ScoringEligibilityQueryService eligibilityQueryService, IdentityService identityService,
            GradingAssignmentRepository assignmentRepository, ScorePublicationLockService publicationLockService,
            SessionGradingGateway gateway, GradingCohortService cohortService) {
        this.sessionService = sessionService;
        this.attemptService = attemptService;
        this.eligibilityQueryService = eligibilityQueryService;
        this.identityService = identityService;
        this.assignmentRepository = assignmentRepository;
        this.publicationLockService = publicationLockService;
        this.gateway = gateway;
        this.cohortService = cohortService;
    }

    /** The editable table: every eligible submitted attempt, or the committed mapping once the Host has locked it. */
    @Transactional(readOnly = true)
    public ExaminerAssignmentRosterResponse roster(UUID sessionPublicId, CurrentUser caller) {
        UUID tenantId = requireHost(caller);
        sessionService.verifyHostAccess(sessionPublicId, tenantId);
        List<GradingAssignment> committed =
                assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionPublicId);
        Map<UUID, AttemptSummaryView> submitted = submittedAttempts(sessionPublicId, tenantId);
        Map<UUID, UUID> examinerByAttempt = new LinkedHashMap<>();
        if (committed.isEmpty()) {
            eligibleAttemptCounts(sessionPublicId, tenantId, submitted)
                    .keySet().forEach(attemptId -> examinerByAttempt.put(attemptId, null));
        } else {
            committed.forEach(row -> examinerByAttempt.put(row.getAttemptPublicId(), row.getExaminerPublicId()));
        }
        Map<UUID, StudentDisplayNameView> students = identityService.getStudentDisplayNames(tenantId,
                submitted.values().stream().map(AttemptSummaryView::studentPublicId).collect(Collectors.toSet()));
        List<ExaminerAssignmentRosterResponse.Row> rows = examinerByAttempt.entrySet().stream()
                .filter(entry -> submitted.containsKey(entry.getKey()))
                .map(entry -> {
                    UUID studentId = submitted.get(entry.getKey()).studentPublicId();
                    StudentDisplayNameView student = students.get(studentId);
                    return new ExaminerAssignmentRosterResponse.Row(entry.getKey(), studentId,
                            student == null ? null : student.displayName(),
                            student == null ? null : student.email(), entry.getValue());
                })
                .sorted(Comparator
                        .comparing((ExaminerAssignmentRosterResponse.Row row) -> row.studentName() == null
                                ? "" : row.studentName().toLowerCase(Locale.ROOT))
                        .thenComparing(row -> row.attemptPublicId().toString()))
                .toList();
        return new ExaminerAssignmentRosterResponse(!committed.isEmpty(), rows);
    }

    /** Persists the Host's final table in one shot; there is no later edit, so the whole roster must be covered. */
    @Transactional
    public ExaminerAssignmentRosterResponse commit(UUID sessionPublicId, CommitExaminerAssignmentRequest request,
            CurrentUser caller) {
        UUID tenantId = requireHost(caller);
        sessionService.lockForExaminerAssignment(sessionPublicId, tenantId);
        publicationLockService.assertNotPublished(tenantId, sessionPublicId);
        if (request == null || request.entries() == null || request.entries().isEmpty()) {
            throw new InvalidExaminerAssignmentException(
                    ExaminerAssignmentConstants.ASSIGNMENT_MUST_COVER_EVERY_ATTEMPT);
        }
        if (!assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionPublicId)
                .isEmpty()) {
            throw new InvalidExaminerAssignmentException(ExaminerAssignmentConstants.ALREADY_ASSIGNED);
        }
        assertGradingAllowsExaminers(tenantId, sessionPublicId);

        Map<UUID, UUID> examinerByAttempt = new LinkedHashMap<>();
        for (CommitExaminerAssignmentRequest.Entry entry : request.entries()) {
            if (entry == null || entry.attemptPublicId() == null || entry.examinerPublicId() == null
                    || examinerByAttempt.put(entry.attemptPublicId(), entry.examinerPublicId()) != null) {
                throw new InvalidExaminerAssignmentException(
                        ExaminerAssignmentConstants.ASSIGNMENT_MUST_COVER_EVERY_ATTEMPT);
            }
        }
        Map<UUID, Integer> eligible = eligibleAttemptCounts(sessionPublicId, tenantId,
                submittedAttempts(sessionPublicId, tenantId));
        if (!eligible.keySet().equals(examinerByAttempt.keySet())) {
            throw new InvalidExaminerAssignmentException(
                    ExaminerAssignmentConstants.ASSIGNMENT_MUST_COVER_EVERY_ATTEMPT);
        }
        List<UUID> examinerIds = examinerByAttempt.values().stream().distinct().toList();
        if (!validateExaminerLocks(tenantId, examinerIds)) {
            throw new InvalidExaminerAssignmentException(
                    ExaminerAssignmentConstants.EXAMINERS_MUST_BE_ACTIVE_AND_TENANT_SCOPED);
        }

        Instant now = Instant.now();
        assignmentRepository.saveAll(examinerByAttempt.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .map(entry -> new GradingAssignment(tenantId, sessionPublicId, entry.getKey(), entry.getValue(),
                        eligible.get(entry.getKey()), caller.userId(), now))
                .toList());
        assignmentRepository.flush();
        cohortService.ensureManualCohort(sessionPublicId, tenantId, caller.userId());
        return roster(sessionPublicId, caller);
    }

    /** What each examiner has been given so far in this session. */
    @Transactional(readOnly = true)
    public ExaminerAssignmentOverviewResponse overview(UUID sessionPublicId, CurrentUser caller) {
        UUID tenantId = requireHost(caller);
        sessionService.verifyHostAccess(sessionPublicId, tenantId);
        List<ExaminerAssignmentLoadResponse> loads = assignmentRepository.summarizeLoads(tenantId, sessionPublicId)
                .stream().map(load -> new ExaminerAssignmentLoadResponse(load.getExaminerPublicId(),
                        load.getAttemptCount(), load.getEligibleAnswerCount()))
                .toList();
        long assignedAttemptCount = assignmentRepository
                .countByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionPublicId);
        return new ExaminerAssignmentOverviewResponse(loads, assignedAttemptCount);
    }

    private Map<UUID, AttemptSummaryView> submittedAttempts(UUID sessionPublicId, UUID tenantId) {
        return attemptService.getSubmittedAttemptsForSession(sessionPublicId, tenantId).stream()
                .collect(Collectors.toMap(AttemptSummaryView::attemptPublicId, attempt -> attempt,
                        (left, right) -> left, LinkedHashMap::new));
    }

    /** Submitted attempts that carry at least one AI-eligible answer and are not excluded from the grading. */
    private Map<UUID, Integer> eligibleAttemptCounts(UUID sessionPublicId, UUID tenantId,
            Map<UUID, AttemptSummaryView> submitted) {
        Set<UUID> excluded = attemptService.getGradingCandidatesForSession(sessionPublicId, tenantId).stream()
                .filter(AttemptGradingCandidateView::excluded)
                .map(AttemptGradingCandidateView::attemptPublicId).collect(Collectors.toSet());
        List<UUID> candidates = submitted.keySet().stream().filter(id -> !excluded.contains(id)).toList();
        return eligibilityQueryService.findEligibleAttempts(sessionPublicId, tenantId, candidates).stream()
                .collect(Collectors.toMap(view -> view.attemptPublicId(), view -> view.eligibleAnswerCount(),
                        (left, right) -> left, LinkedHashMap::new));
    }

    private void assertGradingAllowsExaminers(UUID tenantId, UUID sessionPublicId) {
        gateway.grading(tenantId, sessionPublicId)
                .filter(grading -> grading.markingMode() == GradingMarkingMode.AI_ONLY)
                .ifPresent(grading -> {
                    throw new InvalidExaminerAssignmentException(ExaminerAssignmentConstants.AI_ONLY_COHORT_REJECTED);
                });
    }

    private boolean validateExaminerLocks(UUID tenantId, List<UUID> requestedIds) {
        List<ExaminerIdentityView> active = identityService.lockActiveExaminers(tenantId, requestedIds);
        Set<UUID> activeIds = active.stream().map(ExaminerIdentityView::publicId).collect(Collectors.toSet());
        return activeIds.size() == requestedIds.size() && activeIds.containsAll(requestedIds);
    }

    private UUID requireHost(CurrentUser caller) {
        if (caller == null || caller.tenantId() == null || !caller.hasRole(ExaminerAssignmentConstants.HOST_ROLE)) {
            throw new InvalidExaminerAssignmentException(ExaminerAssignmentConstants.HOST_REQUIRED);
        }
        return caller.tenantId();
    }
}
