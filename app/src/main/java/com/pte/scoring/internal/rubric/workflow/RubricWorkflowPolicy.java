package com.pte.scoring.internal.rubric.workflow;

import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.exception.RubricVersionException;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;
import org.springframework.http.HttpStatus;

public final class RubricWorkflowPolicy {
    private RubricWorkflowPolicy() { }

    public static void author(CurrentUser caller) {
        caller.requireAnyRole(SecurityRoles.PLATFORM_ADMIN, SecurityRoles.ACADEMIC_MANAGER,
                SecurityRoles.ACADEMIC_STAFF, SecurityRoles.LEGACY_PLATFORM_AUTHOR);
    }

    public static void reviewer(CurrentUser caller, ScoringRubricVersion candidate) {
        caller.requireAnyRole(SecurityRoles.PLATFORM_ADMIN, SecurityRoles.ACADEMIC_MANAGER);
        // Managers may activate their own direct edits; reviewing a staff submission has a separation-of-duties rule.
        if (candidate.getStatus() == RubricVersionStatus.PENDING && !caller.hasRole(SecurityRoles.PLATFORM_ADMIN)
                && caller.userId().equals(candidate.getAuthorUserPublicId())) {
            throw new RubricVersionException(HttpStatus.FORBIDDEN, RubricConstants.RUBRIC_SELF_APPROVAL_FORBIDDEN);
        }
    }
}
