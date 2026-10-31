package com.pte.reporting.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.reporting.domain.ExamResult;
import com.pte.reporting.internal.dto.response.ReportResponse;
import com.pte.reporting.internal.exception.ReportNotFoundException;
import com.pte.reporting.internal.mapper.ReportMapper;
import com.pte.reporting.internal.repository.ExamResultRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/** Students read immutable published snapshots; Hosts may still inspect live pre-publication scores. */
@Service
public class ReportService {

    private static final String ROLE_STUDENT = "STUDENT";

    private final ExamResultRepository examResultRepository;
    private final ScoreAggregationService scoreAggregationService;
    private final AttemptService attemptService;
    private final ReportSnapshotCodec snapshotCodec;
    private final SessionService sessionService;

    @Autowired
    public ReportService(ExamResultRepository examResultRepository,
            ScoreAggregationService scoreAggregationService,
            AttemptService attemptService,
            ReportSnapshotCodec snapshotCodec,
            SessionService sessionService) {
        this.examResultRepository = examResultRepository;
        this.scoreAggregationService = scoreAggregationService;
        this.attemptService = attemptService;
        this.snapshotCodec = snapshotCodec;
        this.sessionService = sessionService;
    }

    @Transactional
    public ReportResponse getReport(UUID attemptPublicId, CurrentUser caller) {
        // A student's only copy of a score is the email sent when the host publishes; no read path serves it.
        if (caller == null || caller.hasRole(ROLE_STUDENT)) {
            throw new ReportNotFoundException();
        }
        ExamResult report = examResultRepository.findByAttemptPublicId(attemptPublicId).orElse(null);
        if (report == null) {
            AttemptSummaryView attempt = attemptService.getSubmittedAttempt(attemptPublicId);
            if (!canViewAttempt(attempt, caller)) {
                throw new ReportNotFoundException();
            }
            sessionService.lockForScoreReviewMutation(attempt.sessionPublicId(), attempt.tenantId());
            report = examResultRepository.findByAttemptPublicId(attemptPublicId)
                    .orElseGet(() -> examResultRepository.save(newReport(attempt)));
        }
        if (!canView(report, caller)) {
            throw new ReportNotFoundException();
        }
        if (report.isPublished()) {
            return ReportMapper.toResponse(report, publishedSummary(report));
        }
        ProvisionalScoreSummary live = scoreAggregationService.aggregateProvisional(attemptPublicId,
                report.getTenantId());
        return ReportMapper.toResponse(report, live.summary(), live.includesUnselectedAiScores());
    }

    private AttemptScoreSummary publishedSummary(ExamResult report) {
        if (report.getReportSnapshotJson() == null) {
            return new AttemptScoreSummary(null, Map.of());
        }
        return snapshotCodec.decode(report.getReportSnapshotJson()).scoreSummary();
    }

    static ExamResult newReport(AttemptSummaryView summary) {
        ExamResult report = new ExamResult();
        report.setAttemptPublicId(summary.attemptPublicId());
        report.setSessionPublicId(summary.sessionPublicId());
        report.setStudentPublicId(summary.studentPublicId());
        report.setTenantId(summary.tenantId());
        report.setSnapshotPublicId(summary.snapshotPublicId());
        return report;
    }

    private boolean canView(ExamResult report, CurrentUser caller) {
        return caller.hasRole("PLATFORM_ADMIN") || report.getTenantId().equals(caller.tenantId());
    }

    private boolean canViewAttempt(AttemptSummaryView attempt, CurrentUser caller) {
        return caller.hasRole("PLATFORM_ADMIN") || attempt.tenantId().equals(caller.tenantId());
    }
}
