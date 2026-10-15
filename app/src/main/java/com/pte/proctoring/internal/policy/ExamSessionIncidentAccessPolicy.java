package com.pte.proctoring.internal.policy;

import com.pte.proctoring.internal.constant.ExamSessionIncidentConstants;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class ExamSessionIncidentAccessPolicy {

    private final SessionService sessionService;

    public ExamSessionIncidentAccessPolicy(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public IncidentAccessContext authorize(UUID sessionPublicId, CurrentUser caller) {
        if (caller == null || caller.userId() == null) {
            throw forbidden(ExamSessionIncidentConstants.AUTHENTICATED_CALLER_REQUIRED);
        }

        if (caller.hasRole(SecurityRoles.PROCTOR)) {
            if (caller.tenantId() == null) {
                throw forbidden(ExamSessionIncidentConstants.PROCTOR_TENANT_CONTEXT_REQUIRED);
            }
            boolean assigned = sessionService.isProctorAssignedToSession(
                    sessionPublicId, caller.userId(), caller.tenantId());
            if (!assigned) {
                throw forbidden(ExamSessionIncidentConstants.PROCTOR_NOT_ASSIGNED);
            }
            return context(caller, caller.tenantId(), SecurityRoles.PROCTOR, true);
        }

        if (caller.hasRole(SecurityRoles.HOST_ADMIN)) {
            if (caller.tenantId() == null) {
                throw forbidden(ExamSessionIncidentConstants.HOST_TENANT_CONTEXT_REQUIRED);
            }
            sessionService.verifyHostAccess(sessionPublicId, caller.tenantId());
            return context(caller, caller.tenantId(), SecurityRoles.HOST_ADMIN, false);
        }

        if (caller.hasRole(SecurityRoles.PLATFORM_ADMIN)
                || caller.hasRole(SecurityRoles.PLATFORM_MANAGER)) {
            UUID tenantId = sessionService.resolveTenantId(sessionPublicId);
            return context(caller, tenantId, resolveActorRole(caller), false);
        }

        throw forbidden(ExamSessionIncidentConstants.INCIDENT_ACCESS_DENIED);
    }

    private IncidentAccessContext context(
            CurrentUser caller,
            UUID tenantId,
            String actorRole,
            boolean proctor) {
        return new IncidentAccessContext(
                tenantId,
                caller.userId(),
                actorRole,
                resolveActorName(caller),
                proctor);
    }

    private String resolveActorRole(CurrentUser caller) {
        if (caller == null) {
            return ExamSessionIncidentConstants.UNKNOWN_ACTOR_ROLE;
        }
        if (caller.hasRole(SecurityRoles.PROCTOR)) {
            return SecurityRoles.PROCTOR;
        }
        if (caller.hasRole(SecurityRoles.HOST_ADMIN)) {
            return SecurityRoles.HOST_ADMIN;
        }
        return caller.roles() != null && !caller.roles().isEmpty()
                ? caller.roles().get(0)
                : ExamSessionIncidentConstants.UNKNOWN_ACTOR_ROLE;
    }

    private String resolveActorName(CurrentUser caller) {
        if (caller == null || caller.userId() == null) {
            return ExamSessionIncidentConstants.SYSTEM_ACTOR_NAME;
        }
        return caller.userId().toString();
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
}
