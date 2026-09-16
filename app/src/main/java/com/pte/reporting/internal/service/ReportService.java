package com.pte.reporting.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.reporting.domain.AttemptReport;
import com.pte.reporting.internal.exception.ReportNotFoundException;
import com.pte.reporting.internal.dto.response.ReportResponse;
import com.pte.reporting.internal.mapper.ReportMapper;
import com.pte.reporting.internal.repository.AttemptReportRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Visibility rule: a STUDENT sees a report only if it's {@code published}
 * AND they own the attempt; a host sees any time, scoped to their own
 * tenant (review-before-publish). Both denial paths throw {@link
 * ReportNotFoundException} (404) — never a 403 that would leak whether the
 * attempt exists.
 *
 * <p>{@link AttemptReport} rows are created lazily here on first access, not
 * eagerly at attempt-submit time — attempt has no outbound dependency on
 * reporting (dependency order: attempt ──> reporting, not the reverse), so
 * reporting pulls from {@code attempt}'s public API instead of attempt
 * pushing an event.
 */
@Service
public class ReportService {

    private static final String ROLE_STUDENT = "STUDENT";

    private final AttemptReportRepository attemptReportRepository;
    private final ScoreAggregationService scoreAggregationService;
    private final AttemptService attemptService;

    public ReportService(AttemptReportRepository attemptReportRepository,
                         ScoreAggregationService scoreAggregationService, AttemptService attemptService) {
        this.attemptReportRepository = attemptReportRepository;
        this.scoreAggregationService = scoreAggregationService;
        this.attemptService = attemptService;
    }

    @Transactional
    public ReportResponse getReport(UUID attemptPublicId, CurrentUser caller) {
        AttemptReport report = attemptReportRepository.findByAttemptPublicId(attemptPublicId)
                .orElseGet(() -> createFromAttempt(attemptPublicId));
        if (!canView(report, caller)) {
            throw new ReportNotFoundException();
        }
        AttemptScoreSummary summary = report.getSnapshotPublicId() == null
                ? scoreAggregationService.aggregate(attemptPublicId, report.getTenantId())
                : scoreAggregationService.aggregate(attemptPublicId, report.getTenantId(), report.getSnapshotPublicId());
        return ReportMapper.toResponse(report, summary);
    }

    /**
     * Propagates attempt's own not-found/not-submitted exception unmodified
     * (same 404 shape) when the attempt doesn't exist yet — a report simply
     * can't exist before the attempt does.
     */
    private AttemptReport createFromAttempt(UUID attemptPublicId) {
        AttemptSummaryView summary = attemptService.getSubmittedAttempt(attemptPublicId);
        AttemptReport report = newReport(summary);
        try {
            return attemptReportRepository.save(report);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent first-access race — the unique constraint on attemptPublicId already dedups it.
            return attemptReportRepository.findByAttemptPublicId(attemptPublicId).orElseThrow(ReportNotFoundException::new);
        }
    }

    static AttemptReport newReport(AttemptSummaryView summary) {
        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(summary.attemptPublicId());
        report.setSessionPublicId(summary.sessionPublicId());
        report.setStudentPublicId(summary.studentPublicId());
        report.setTenantId(summary.tenantId());
        report.setSnapshotPublicId(summary.snapshotPublicId());
        return report;
    }

    private boolean canView(AttemptReport report, CurrentUser caller) {
        if (caller.hasRole(ROLE_STUDENT)) {
            return report.isPublished() && report.getStudentPublicId().equals(caller.userId());
        }
        return caller.isPlatformUser() || report.getTenantId().equals(caller.tenantId());
    }
}
