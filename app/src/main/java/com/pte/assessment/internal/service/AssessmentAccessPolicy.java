package com.pte.assessment.internal.service;

import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityPolicy;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Read scope rule for blueprint/snapshot aggregates (ADR-001 visibility model,
 * mirrored from {@code itembank}'s own policy): a null {@code tenantId} means
 * platform-shared, world-readable; otherwise readable only by its owning
 * tenant (platform users may read across tenants for support).
 */
@Component
public class AssessmentAccessPolicy {

    public boolean canRead(UUID entityTenantId, boolean shared, CurrentUser caller) {
        if (shared) {
            return true;
        }
        if (caller.isPlatformUser()) {
            return true;
        }
        return entityTenantId != null && entityTenantId.equals(caller.tenantId());
    }

    public boolean canAuthor(CurrentUser caller) {
        return SecurityPolicy.canCreateAcademicDraft(caller);
    }

    public boolean canModifyDraft(CurrentUser caller, UUID authorUserPublicId) {
        return SecurityPolicy.canModifyAcademicDraft(caller, authorUserPublicId);
    }

    public boolean canApprove(CurrentUser caller, UUID authorUserPublicId) {
        return SecurityPolicy.canReviewAcademic(caller, authorUserPublicId);
    }

    public boolean canPublish(CurrentUser caller, UUID authorUserPublicId) {
        return SecurityPolicy.canPublishAcademic(caller, authorUserPublicId);
    }
}
