package com.pte.itembank.internal.service;

import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityPolicy;
import com.pte.shared.security.SecurityCapability;
import org.springframework.stereotype.Component;

/** Platform question-bank write policy after the private bank is retired. */
@Component
public class ItembankAccessPolicy {

    public boolean canWrite(CurrentUser caller) {
        return SecurityPolicy.canCreateAcademicDraft(caller);
    }

    public boolean canApprove(CurrentUser caller) {
        return SecurityPolicy.hasCapability(caller, SecurityCapability.ACADEMIC_APPROVE);
    }

    public boolean canModifyDraft(CurrentUser caller, java.util.UUID authorUserPublicId) {
        return SecurityPolicy.canModifyAcademicDraft(caller, authorUserPublicId);
    }

    public boolean canReview(CurrentUser caller, java.util.UUID authorUserPublicId) {
        return SecurityPolicy.canReviewAcademic(caller, authorUserPublicId);
    }

    public boolean canPublish(CurrentUser caller, java.util.UUID authorUserPublicId) {
        return SecurityPolicy.canPublishAcademic(caller, authorUserPublicId);
    }
}
