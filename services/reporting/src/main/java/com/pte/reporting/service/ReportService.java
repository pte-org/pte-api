package com.pte.reporting.service;

import com.pte.common.security.CurrentUser;
import com.pte.reporting.domain.AttemptReport;
import com.pte.reporting.domain.exception.ReportNotFoundException;
import com.pte.reporting.dto.response.ReportResponse;
import com.pte.reporting.mapper.ReportMapper;
import com.pte.reporting.repository.AttemptReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Visibility rule (phase-08): a STUDENT sees a report only if it's
 * {@code published} AND they own the attempt; a host sees any time, scoped to
 * their own tenant. Both denial paths return {@link ReportNotFoundException}
 * (404) — never a 403 that would leak whether the attempt exists.
 */
@Service
public class ReportService {

    private static final String ROLE_STUDENT = "STUDENT";

    private final AttemptReportRepository attemptReportRepository;
    private final ScoreAggregationService scoreAggregationService;

    public ReportService(AttemptReportRepository attemptReportRepository,
                         ScoreAggregationService scoreAggregationService) {
        this.attemptReportRepository = attemptReportRepository;
        this.scoreAggregationService = scoreAggregationService;
    }

    @Transactional(readOnly = true)
    public ReportResponse getReport(UUID attemptPublicId, CurrentUser caller) {
        AttemptReport report = attemptReportRepository.findByAttemptPublicId(attemptPublicId)
                .orElseThrow(ReportNotFoundException::new);
        if (!canView(report, caller)) {
            throw new ReportNotFoundException();
        }
        AttemptScoreSummary summary = scoreAggregationService.aggregate(attemptPublicId);
        return ReportMapper.toResponse(report, summary);
    }

    private boolean canView(AttemptReport report, CurrentUser caller) {
        if (caller.hasRole(ROLE_STUDENT)) {
            return report.isPublished() && report.getStudentPublicId().equals(caller.userId());
        }
        return caller.isPlatformUser() || report.getTenantId().equals(caller.tenantId());
    }
}
