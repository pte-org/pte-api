package com.pte.reporting;

import com.pte.reporting.internal.repository.ExamResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-only reporting counts for dashboards. Tenant-scoped and soft-delete
 * filtered; uses only this module's own repository.
 */
@Service
public class ReportingOverviewService {

    private final ExamResultRepository reportRepository;

    public ReportingOverviewService(ExamResultRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /** Attempt reports of the tenant that have been created but not yet published to the student. */
    @Transactional(readOnly = true)
    public long countAwaitingPublication(UUID tenantId) {
        return reportRepository.countByTenantIdAndPublishedFalseAndDeletedFalse(tenantId);
    }
}
