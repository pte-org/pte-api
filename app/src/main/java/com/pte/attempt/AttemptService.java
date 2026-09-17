package com.pte.attempt;

import com.pte.attempt.dto.response.AttemptScoreContextView;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.attempt.internal.service.AttemptSummaryQueryService;
import com.pte.attempt.internal.service.ProctorCommandService;
import com.pte.attempt.internal.service.SubmittedAnswerQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

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
public class AttemptService {

    private final ProctorCommandService proctorCommandService;
    private final SubmittedAnswerQueryService submittedAnswerQueryService;
    private final AttemptSummaryQueryService attemptSummaryQueryService;

    public AttemptService(ProctorCommandService proctorCommandService,
                          SubmittedAnswerQueryService submittedAnswerQueryService,
                          AttemptSummaryQueryService attemptSummaryQueryService) {
        this.proctorCommandService = proctorCommandService;
        this.submittedAnswerQueryService = submittedAnswerQueryService;
        this.attemptSummaryQueryService = attemptSummaryQueryService;
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
}
