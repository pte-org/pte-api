package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A platform-level (non-tenant) caller attempted a host-scoped session action. */
public class HostContextRequiredException extends DomainException {

    public HostContextRequiredException() {
        super(HttpStatus.FORBIDDEN, SessionConstants.HOST_CONTEXT_REQUIRED);
    }
}
