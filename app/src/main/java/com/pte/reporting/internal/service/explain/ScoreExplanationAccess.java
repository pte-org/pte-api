package com.pte.reporting.internal.service.explain;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.reporting.internal.constant.ReportingConstants;
import com.pte.reporting.internal.exception.ReportNotFoundException;
import com.pte.scoring.ScoringService;
import com.pte.shared.exception.DomainException;
import com.pte.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Who may read an attempt's score explanation: a Host of the attempt's own tenant, or an Examiner of that tenant who
 * is assigned to the attempt. Everything else — a missing or unsubmitted attempt, another tenant's attempt, an
 * Examiner not assigned to it, a role without access — is the same 404 {@code REPORT_NOT_FOUND}, so a caller cannot
 * learn whether an attempt exists outside what they may see (same rule as {@code ReportService}). The controller's
 * role guard already returns 403 to every other role; this check is the tenant and assignment layer behind it.
 */
@Component
public class ScoreExplanationAccess {

    private final AttemptService attemptService;
    private final ScoringService scoringService;

    public ScoreExplanationAccess(AttemptService attemptService, ScoringService scoringService) {
        this.attemptService = attemptService;
        this.scoringService = scoringService;
    }

    public AttemptSummaryView requireReadable(UUID attemptPublicId, CurrentUser caller) {
        if (caller == null || caller.tenantId() == null) {
            throw new ReportNotFoundException();
        }
        boolean host = caller.hasRole(ReportingConstants.ROLE_HOST_ADMIN);
        boolean examiner = caller.hasRole(ReportingConstants.ROLE_EXAMINER);
        if (!host && !examiner) {
            throw new ReportNotFoundException();
        }
        AttemptSummaryView attempt = submittedAttempt(attemptPublicId);
        if (!caller.tenantId().equals(attempt.tenantId())) {
            throw new ReportNotFoundException();
        }
        if (host) {
            return attempt;
        }
        if (!scoringService.isAttemptAssignedToExaminer(attempt.tenantId(), attempt.sessionPublicId(),
                attemptPublicId, caller.userId())) {
            throw new ReportNotFoundException();
        }
        return attempt;
    }

    /** A missing or unsubmitted attempt reports the same code as a hidden one. */
    private AttemptSummaryView submittedAttempt(UUID attemptPublicId) {
        try {
            return attemptService.getSubmittedAttempt(attemptPublicId);
        } catch (DomainException notFound) {
            if (notFound.getStatus() == HttpStatus.NOT_FOUND) {
                throw new ReportNotFoundException();
            }
            throw notFound;
        }
    }
}
