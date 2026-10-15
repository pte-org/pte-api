package com.pte.proctoring.internal.policy;

import com.pte.proctoring.domain.ProctorSession;
import com.pte.proctoring.internal.exception.ProctorSessionNotActiveException;
import com.pte.proctoring.internal.exception.ProctorSessionNotFoundException;
import com.pte.proctoring.internal.repository.ProctorSessionRepository;
import com.pte.session.SessionService;
import com.pte.session.dto.response.ProctorAssignmentCheckResponse;
import com.pte.shared.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

/**
 * Centralized policy for PROCTOR-assigned reads, control gates, owned-active monitoring sessions,
 * and close operations.
 */
@Component
public class ProctorAccessPolicy {

    private final SessionService sessionService;
    private final ProctorSessionRepository proctorSessionRepository;

    public ProctorAccessPolicy(SessionService sessionService, ProctorSessionRepository proctorSessionRepository) {
        this.sessionService = sessionService;
        this.proctorSessionRepository = proctorSessionRepository;
    }

    /**
     * Authorizes reading assigned exam session resources (e.g. security audit, legacy violations).
     * Accepts assigned PROCTOR or tenant HOST_ADMIN; prohibits unassigned proctors even within the same tenant.
     */
    public UUID authorizeReadAssigned(UUID sessionPublicId, CurrentUser caller) {
        if (caller == null) {
            throw new AccessDeniedException("Authenticated user is required");
        }
        if (caller.hasRole("PROCTOR")) {
            ProctorAssignmentCheckResponse assignment = sessionService.checkProctorAssignment(
                    sessionPublicId, caller.userId());
            if (caller.tenantId() != null && !Objects.equals(assignment.tenantId(), caller.tenantId())) {
                throw new AccessDeniedException("Tenant mismatch on proctor assignment");
            }
            return assignment.tenantId();
        }
        if (caller.hasRole("HOST_ADMIN")) {
            if (caller.tenantId() == null) {
                throw new AccessDeniedException("Tenant context is required");
            }
            sessionService.verifyHostAccess(sessionPublicId, caller.tenantId());
            return caller.tenantId();
        }
        throw new AccessDeniedException("Assigned PROCTOR or HOST_ADMIN role is required");
    }

    /**
     * Strictly requires caller to be an assigned PROCTOR for this session within the caller's tenant.
     */
    public UUID authorizeProctorAssigned(UUID sessionPublicId, CurrentUser caller) {
        if (caller == null) {
            throw new AccessDeniedException("Authenticated user is required");
        }
        if (!caller.hasRole("PROCTOR")) {
            throw new AccessDeniedException("PROCTOR role is required");
        }
        ProctorAssignmentCheckResponse assignment = sessionService.checkProctorAssignment(
                sessionPublicId, caller.userId());
        if (caller.tenantId() != null && !Objects.equals(assignment.tenantId(), caller.tenantId())) {
            throw new AccessDeniedException("Tenant mismatch on proctor assignment");
        }
        return assignment.tenantId();
    }

    /**
     * Authorizes access to an ACTIVE owned proctor session.
     */
    public ProctorSession authorizeOwnedActive(UUID proctorSessionPublicId, CurrentUser caller) {
        if (caller == null) {
            throw new AccessDeniedException("Authenticated user is required");
        }
        ProctorSession session = proctorSessionRepository
                .findByPublicIdAndProctorPublicIdAndTenantId(proctorSessionPublicId, caller.userId(), caller.tenantId())
                .orElseThrow(ProctorSessionNotFoundException::new);
        if (!session.isActive()) {
            throw new ProctorSessionNotActiveException();
        }
        return session;
    }

    /**
     * Authorizes closing an owned proctor session (idempotent, permits closing even after revocation/close).
     */
    public ProctorSession authorizeCloseOwned(UUID proctorSessionPublicId, CurrentUser caller) {
        if (caller == null) {
            throw new AccessDeniedException("Authenticated user is required");
        }
        return proctorSessionRepository
                .findByPublicIdAndProctorPublicIdAndTenantId(proctorSessionPublicId, caller.userId(), caller.tenantId())
                .orElseThrow(ProctorSessionNotFoundException::new);
    }
}
