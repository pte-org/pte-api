package com.pte.session.internal.exception.capacity;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.UUID;
import java.util.Map;

public class SessionTimeConflictException extends DomainException {

    public SessionTimeConflictException() {
        super(HttpStatus.CONFLICT, SessionConstants.SESSION_TIME_CONFLICT,
                null, SessionConstants.SESSION_TIME_CONFLICT_FRIENDLY);
    }

    public SessionTimeConflictException(UUID conflictingSessionPublicId) {
        super(HttpStatus.CONFLICT, SessionConstants.SESSION_TIME_CONFLICT,
                conflictingSessionPublicId, SessionConstants.SESSION_TIME_CONFLICT_FRIENDLY,
                SessionConstants.SESSION_TIME_CONFLICT + ": "
                        + String.format(SessionConstants.SESSION_TIME_CONFLICT_DETAIL, conflictingSessionPublicId));
    }

    public SessionTimeConflictException(UUID conflictingSessionPublicId, String conflictingSessionName) {
        super(HttpStatus.CONFLICT, SessionConstants.SESSION_TIME_CONFLICT,
                Map.of("conflictingSessionPublicId", conflictingSessionPublicId,
                        "conflictingSessionName", conflictingSessionName == null
                                ? conflictingSessionPublicId.toString() : conflictingSessionName),
                SessionConstants.SESSION_TIME_CONFLICT_FRIENDLY,
                SessionConstants.SESSION_TIME_CONFLICT + ": "
                        + String.format(SessionConstants.SESSION_TIME_CONFLICT_DETAIL, conflictingSessionPublicId));
    }
}
