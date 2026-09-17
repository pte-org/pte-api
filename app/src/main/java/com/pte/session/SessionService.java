package com.pte.session;

import com.pte.session.dto.response.EntitlementResponse;
import com.pte.session.dto.response.ProctorAssignmentCheckResponse;
import com.pte.session.internal.service.EntitlementService;
import com.pte.session.internal.service.SessionLifecycleService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The only door other modules use to reach {@code session}. {@code
 * SessionLifecycleService}, repositories, and controllers stay in {@code
 * internal/}.
 *
 * <p>Trusted-caller checks known to have cross-module callers: {@code
 * attempt} (Phase 07) verifying a student's standing before pinning an
 * attempt, {@code proctoring} (Phase 09) verifying a proctor's assignment
 * before opening a proctor session, and {@code scoring} (Phase 08) verifying
 * a host owns a session before triggering its scoring command. Session CRUD,
 * composition, and enrollment stay internal — only a host's own scheduling UI
 * calls those, through the respective controllers directly.
 */
@Service
public class SessionService {

    private final EntitlementService entitlementService;
    private final SessionLifecycleService sessionLifecycleService;

    public SessionService(EntitlementService entitlementService, SessionLifecycleService sessionLifecycleService) {
        this.entitlementService = entitlementService;
        this.sessionLifecycleService = sessionLifecycleService;
    }

    /** Throws if the session isn't OPEN or the student isn't enrolled — never returns a false/empty result for "not entitled". */
    public EntitlementResponse checkEntitlement(UUID sessionPublicId, UUID studentPublicId) {
        return entitlementService.checkEntitlement(sessionPublicId, studentPublicId);
    }

    /** Throws if the proctor isn't assigned to the session. */
    public ProctorAssignmentCheckResponse checkProctorAssignment(UUID sessionPublicId, UUID proctorPublicId) {
        return entitlementService.checkProctorAssignment(sessionPublicId, proctorPublicId);
    }

    /** Throws if the session doesn't exist in the caller's tenant. */
    public void verifyHostAccess(UUID sessionPublicId, UUID tenantId) {
        entitlementService.verifyHostAccess(sessionPublicId, tenantId);
    }

    /** Cancels scheduled sessions for a revoked subscription; open/closed sessions are untouched. */
    public void cancelScheduledSessionsBySubscription(UUID subscriptionId) {
        sessionLifecycleService.cancelScheduledSessionsBySubscription(subscriptionId);
    }
}
