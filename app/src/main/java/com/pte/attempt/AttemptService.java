package com.pte.attempt;

import com.pte.attempt.dto.response.AttemptScoreContextView;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.attempt.dto.response.AttemptExaminerPromptView;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.attempt.internal.service.AttemptSummaryQueryService;
import com.pte.attempt.internal.service.AttemptExaminerPromptQueryService;
import com.pte.attempt.internal.service.ProctorCommandService;
import com.pte.attempt.internal.service.SubmittedAnswerQueryService;
import com.pte.shared.StartedAttemptLookup;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The only door other modules use to reach {@code attempt}. {@code
 * AttemptLifecycleService}, repositories, and controllers stay in {@code
 * internal/}.
 *
 * <p>Known cross-module callers: {@code proctoring} (Phase 09) issuing a
 * force-submit command, {@code scoring} (Phase 08) pulling submitted answers
 * to ingest, and {@code reporting} (Phase 10) pulling submitted attempts to
 * create/publish reports. Everything else a student does against their own
 * attempt stays internal — only {@code AttemptController}/{@code
 * HeartbeatController} call those, through {@code AttemptLifecycleService}
 * directly.
 */
@Service
public class AttemptService implements StartedAttemptLookup {

    private final ProctorCommandService proctorCommandService;
    private final SubmittedAnswerQueryService submittedAnswerQueryService;
    private final AttemptSummaryQueryService attemptSummaryQueryService;
    private final AttemptExaminerPromptQueryService attemptExaminerPromptQueryService;
    private final ExamAttemptRepository examAttemptRepository;

    public AttemptService(ProctorCommandService proctorCommandService,
                          SubmittedAnswerQueryService submittedAnswerQueryService,
                          AttemptSummaryQueryService attemptSummaryQueryService,
                          AttemptExaminerPromptQueryService attemptExaminerPromptQueryService,
                          ExamAttemptRepository examAttemptRepository) {
        this.proctorCommandService = proctorCommandService;
        this.submittedAnswerQueryService = submittedAnswerQueryService;
        this.attemptSummaryQueryService = attemptSummaryQueryService;
        this.attemptExaminerPromptQueryService = attemptExaminerPromptQueryService;
        this.examAttemptRepository = examAttemptRepository;
    }

    /** Silent no-op if the attempt doesn't exist or isn't IN_PROGRESS — a stale/duplicate/late command is not an error. */
    public void forceSubmit(UUID attemptPublicId, UUID tenantId) {
        proctorCommandService.forceSubmit(attemptPublicId, tenantId);
    }

    /** Every answer submitted so far for the session (any attempt, any status) — scoring's ingestion pull (Phase 08). */
    public List<SubmittedAnswerView> getSubmittedAnswersForSession(UUID sessionPublicId, UUID tenantId) {
        return submittedAnswerQueryService.findForSession(sessionPublicId, tenantId);
    }

    /** Throws if the attempt doesn't exist or hasn't reached SUBMITTED — reporting's report creation pull (Phase 10). */
    public AttemptSummaryView getSubmittedAttempt(UUID attemptPublicId) {
        return attemptSummaryQueryService.findSubmitted(attemptPublicId);
    }

    /** Every submitted attempt in a session — reporting's publish fanout (Phase 10). */
    public List<AttemptSummaryView> getSubmittedAttemptsForSession(UUID sessionPublicId, UUID tenantId) {
        return attemptSummaryQueryService.findSubmittedForSession(sessionPublicId, tenantId);
    }

    /** The pinned score template + tested sections for one attempt — reporting's weighted scoring (Phase 5). */
    public AttemptScoreContextView getScoreContext(UUID attemptPublicId) {
        return attemptSummaryQueryService.getScoreContext(attemptPublicId);
    }

    /** Batch pinned scoring contexts for report publication; attempt contents stay inside this module. */
    public Map<UUID, AttemptScoreContextView> getScoreContexts(Collection<UUID> attemptPublicIds) {
        return attemptSummaryQueryService.getScoreContexts(attemptPublicIds);
    }

    /**
     * Reads answer-key-free prompts from the attempt's immutable pinned items
     * for an already assignment-authorized Examiner workflow.
     */
    public Map<UUID, AttemptExaminerPromptView> getExaminerPrompts(UUID attemptPublicId, UUID sessionPublicId,
            UUID tenantId, Collection<UUID> pinnedItemPublicIds) {
        return attemptExaminerPromptQueryService.findForExaminer(attemptPublicId, sessionPublicId, tenantId,
                pinnedItemPublicIds);
    }

    /** Batch conflict read for session publish; no attempt content crosses the module boundary. */
    @Override
    public Set<UUID> findStartedStudentPublicIds(UUID tenantId, List<UUID> sessionPublicIds,
            List<UUID> studentPublicIds) {
        if (sessionPublicIds.isEmpty() || studentPublicIds.isEmpty()) {
            return Set.of();
        }
        return examAttemptRepository
                .findByTenantIdAndSessionPublicIdInAndStudentPublicIdInAndStatus(
                        tenantId, sessionPublicIds, studentPublicIds, AttemptStatus.IN_PROGRESS)
                .stream()
                .map(com.pte.attempt.domain.ExamAttempt::getStudentPublicId)
                .collect(Collectors.toSet());
    }
}
