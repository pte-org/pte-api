package com.pte.reporting.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.reporting.domain.AttemptReport;
import com.pte.reporting.dto.event.AttemptPublishedEvent;
import com.pte.reporting.internal.repository.AttemptReportRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Executes a host's "publish this session's reports" command (ADR-002
 * host-gated model). Pulls every submitted attempt in the session from
 * {@code attempt} (find-or-create the {@link AttemptReport} row, same as
 * {@link ReportService}'s lazy-view path), marks each unpublished one
 * published, and emits {@link AttemptPublishedEvent} per newly-published
 * attempt for {@code notification} to email the student — publish is a
 * visibility gate, not a data freeze; the report keeps reflecting live
 * scoring state after publish.
 */
@Service
public class ReportPublishService {

    private final AttemptService attemptService;
    private final AttemptReportRepository attemptReportRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ReportPublishService(AttemptService attemptService, AttemptReportRepository attemptReportRepository,
                                ApplicationEventPublisher eventPublisher) {
        this.attemptService = attemptService;
        this.attemptReportRepository = attemptReportRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void publishSession(UUID sessionPublicId, UUID tenantId) {
        List<AttemptSummaryView> attempts = attemptService.getSubmittedAttemptsForSession(sessionPublicId, tenantId);
        for (AttemptSummaryView summary : attempts) {
            AttemptReport report = findOrCreate(summary);
            if (report.isPublished()) {
                continue;
            }
            report.publish();
            attemptReportRepository.save(report);
            eventPublisher.publishEvent(new AttemptPublishedEvent(report.getAttemptPublicId(),
                    report.getSessionPublicId(), report.getStudentPublicId(), report.getTenantId()));
        }
    }

    private AttemptReport findOrCreate(AttemptSummaryView summary) {
        return attemptReportRepository.findByAttemptPublicId(summary.attemptPublicId())
                .orElseGet(() -> save(ReportService.newReport(summary)));
    }

    private AttemptReport save(AttemptReport report) {
        try {
            return attemptReportRepository.save(report);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent first-access race — the unique constraint on attemptPublicId already dedups it.
            return attemptReportRepository.findByAttemptPublicId(report.getAttemptPublicId()).orElseThrow();
        }
    }
}
