package com.pte.proctoring.internal.service;

import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.proctoring.internal.dto.response.ProctorSessionResponse;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Opens a proctor live monitoring of an exam session. Watching is not stored: it is allowed exactly while the
 * session is open within its window and the caller is still assigned to it, and both are re-checked on every
 * command. The "proctor session" a client sees is the exam session itself, so its id is the exam session id.
 */
@Service
public class ProctorWatchService {

    private final SessionService sessionService;
    private final Clock clock;

    public ProctorWatchService(SessionService sessionService, Clock clock) {
        this.sessionService = sessionService;
        this.clock = clock;
    }

    @Transactional
    public ProctorSessionResponse open(UUID sessionPublicId, CurrentUser caller) {
        authorizeCommand(sessionPublicId, caller);
        return new ProctorSessionResponse(sessionPublicId, sessionPublicId, caller.userId(),
                ProctorConstants.WATCH_STATUS_ACTIVE, clock.instant());
    }

    /** Re-authorizes a command: session open within its window and caller still assigned; returns the tenant. */
    @Transactional
    public UUID authorizeCommand(UUID sessionPublicId, CurrentUser caller) {
        // Lock session and check OPEN + window first to eliminate the race with host revocation.
        sessionService.lockOpenWithinWindowForProctorOperation(sessionPublicId, caller.tenantId());
        return sessionService.checkProctorAssignment(sessionPublicId, caller.userId()).tenantId();
    }
}
