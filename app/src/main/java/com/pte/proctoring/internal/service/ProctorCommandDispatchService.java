package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.proctoring.internal.dto.response.AttemptActionResultResponse;
import com.pte.proctoring.internal.exception.TargetAttemptInvalidException;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Applies a proctor's force-submit to one attempt through attempt's public API. The session must be open within
 * its window and the caller still assigned to it; both are checked on every call.
 */
@Service
public class ProctorCommandDispatchService {

    private final SessionService sessionService;
    private final AttemptService attemptService;

    public ProctorCommandDispatchService(SessionService sessionService, AttemptService attemptService) {
        this.sessionService = sessionService;
        this.attemptService = attemptService;
    }

    @Transactional
    public AttemptActionResultResponse forceSubmit(UUID sessionPublicId, UUID attemptPublicId, CurrentUser caller) {
        // Lock the session and check OPEN + window first to eliminate the race with host revocation.
        sessionService.lockOpenWithinWindowForProctorOperation(sessionPublicId, caller.tenantId());
        UUID tenantId = sessionService.checkProctorAssignment(sessionPublicId, caller.userId()).tenantId();
        if (!attemptService.attemptBelongsToSession(attemptPublicId, sessionPublicId, tenantId)) {
            throw new TargetAttemptInvalidException();
        }
        boolean applied = attemptService.forceSubmitScoped(attemptPublicId, sessionPublicId, tenantId);
        return AttemptActionResultResponse.of(attemptPublicId, applied);
    }
}
