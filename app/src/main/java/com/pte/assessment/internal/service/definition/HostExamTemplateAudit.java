package com.pte.assessment.internal.service.definition;

import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.assessment.internal.exception.ExamDefinitionValidationException;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;

/** Audit trail writes for host template changes; a missing caller or log service is silently skipped. */
final class HostExamTemplateAudit {

    private static final int MAX_SUMMARY_LENGTH = 500;

    private final AuditLogService auditLogService;

    HostExamTemplateAudit(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    void record(CurrentUser caller, String action, ExamDefinition template, String summary) {
        if (auditLogService != null && caller != null) {
            auditLogService.record(caller, ExamDefinitionConstants.AUDIT_AGGREGATE_TYPE,
                    String.valueOf(template.getPublicId()), action, truncate(summary));
        }
    }

    void failure(CurrentUser caller, ExamDefinition template, ExamDefinitionValidationException ex) {
        if (auditLogService != null && caller != null) {
            auditLogService.recordFailure(caller, ExamDefinitionConstants.AUDIT_AGGREGATE_TYPE,
                    String.valueOf(template.getPublicId()), ExamDefinitionConstants.AUDIT_VALIDATION_FAILED,
                    truncate(ex.getMessage()));
        }
    }

    private static String truncate(String summary) {
        return summary == null ? "" : summary.length() <= MAX_SUMMARY_LENGTH ? summary
                : summary.substring(0, MAX_SUMMARY_LENGTH);
    }
}
