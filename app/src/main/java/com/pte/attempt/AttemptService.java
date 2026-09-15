package com.pte.attempt;

import com.pte.attempt.dto.response.SubmittedAnswerView;
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
 * <p>Two known cross-module callers: {@code proctoring} (Phase 09) issuing a
 * force-submit command, and {@code scoring} (Phase 08) pulling submitted
 * answers to ingest. Everything else a student does against their own
 * attempt stays internal — only {@code AttemptController}/{@code
 * HeartbeatController} call those, through {@code AttemptLifecycleService}
 * directly.
 */
@Service
public class AttemptService {

    private final ProctorCommandService proctorCommandService;
    private final SubmittedAnswerQueryService submittedAnswerQueryService;

    public AttemptService(ProctorCommandService proctorCommandService,
                          SubmittedAnswerQueryService submittedAnswerQueryService) {
        this.proctorCommandService = proctorCommandService;
        this.submittedAnswerQueryService = submittedAnswerQueryService;
    }

    /** Silent no-op if the attempt doesn't exist or isn't IN_PROGRESS — a stale/duplicate/late command is not an error. */
    public void forceSubmit(UUID attemptPublicId, UUID tenantId) {
        proctorCommandService.forceSubmit(attemptPublicId, tenantId);
    }

    /** Every answer submitted so far for the session (any attempt, any status) — scoring's ingestion pull (Phase 08). */
    public List<SubmittedAnswerView> getSubmittedAnswersForSession(UUID sessionPublicId, UUID tenantId) {
        return submittedAnswerQueryService.findForSession(sessionPublicId, tenantId);
    }
}
