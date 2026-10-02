package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** The skills-only create request cannot safely express the canonical workflow. */
public class LegacySessionCreateRequiresNewWorkflowException extends DomainException {

    public LegacySessionCreateRequiresNewWorkflowException() {
        super(HttpStatus.CONFLICT, SessionConstants.LEGACY_SESSION_CREATE_REQUIRES_NEW_WORKFLOW, null,
                SessionConstants.LEGACY_SESSION_CREATE_FRIENDLY);
    }
}
