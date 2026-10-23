package com.pte.rubric.internal.service;

import com.pte.rubric.internal.constant.RubricConstants;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityPolicy;
import com.pte.shared.security.SecurityRoles;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.Objects;

import com.pte.rubric.domain.RubricRevision;
import com.pte.rubric.domain.enums.RubricStatus;

@Component
public class RubricAccessPolicy {

    private final AuditLogService auditLogService;

    public RubricAccessPolicy(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public void requireDraftCreate(CurrentUser caller) {
        if (!SecurityPolicy.canCreateAcademicDraft(caller)) {
            deny(caller, null, RubricConstants.RUBRIC_DRAFT_WRITE_REQUIRED);
        }
    }

    public void requireDraftModify(CurrentUser caller, UUID authorUserPublicId, UUID aggregateId) {
        if (!SecurityPolicy.canModifyAcademicDraft(caller, authorUserPublicId)) {
            deny(caller, aggregateId, RubricConstants.RUBRIC_DRAFT_WRITE_REQUIRED);
        }
    }

    public void requireReview(CurrentUser caller, UUID authorUserPublicId, UUID aggregateId) {
        if (!SecurityPolicy.canReviewAcademic(caller, authorUserPublicId)) {
            deny(caller, aggregateId, RubricConstants.RUBRIC_PUBLISH_FORBIDDEN);
        }
    }

    /**
     * Draft and pending revisions are private working material. A staff author
     * may clone only their own working revision; reviewers may clone any
     * revision they are allowed to inspect.
     */
    public void requireCloneReadable(CurrentUser caller, RubricRevision source) {
        if (caller == null || source == null) {
            deny(caller, source == null ? null : source.getPublicId(), RubricConstants.RUBRIC_DRAFT_WRITE_REQUIRED);
        }
        if (source.getStatus() == RubricStatus.PUBLISHED
                || source.getStatus() == RubricStatus.RETIRED
                || caller.hasRole(SecurityRoles.PLATFORM_ADMIN)
                || caller.hasRole(SecurityRoles.ACADEMIC_MANAGER)) {
            return;
        }
        if (!Objects.equals(caller.userId(), source.getAuthorUserPublicId())) {
            deny(caller, source.getPublicId(), RubricConstants.RUBRIC_DRAFT_WRITE_REQUIRED);
        }
    }

    public boolean isPlatformAdmin(CurrentUser caller) {
        return caller != null && caller.hasRole(SecurityRoles.PLATFORM_ADMIN);
    }

    private void deny(CurrentUser caller, UUID aggregateId, String message) {
        if (caller != null) {
            auditLogService.recordFailure(caller, RubricConstants.AUDIT_AGGREGATE_TYPE,
                    aggregateId == null ? "unknown" : aggregateId.toString(),
                    RubricConstants.AUDIT_AUTHORIZATION_DENIED, message);
        }
        throw new AccessDeniedException(message);
    }
}
