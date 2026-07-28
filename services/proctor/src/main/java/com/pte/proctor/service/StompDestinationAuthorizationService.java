package com.pte.proctor.service;

import com.pte.common.security.CurrentUser;
import com.pte.proctor.constant.ProctorConstants;
import com.pte.proctor.domain.enums.ProctorSessionStatus;
import com.pte.proctor.domain.exception.StompCommandForbiddenException;
import com.pte.proctor.domain.exception.StompSubscriptionForbiddenException;
import com.pte.proctor.repository.ProctorSessionRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class StompDestinationAuthorizationService {

    private static final String ROLE_PROCTOR = "PROCTOR";
    private static final String ROLE_HOST_ADMIN = "HOST_ADMIN";
    private static final String USER_PROCTOR_SESSION = "/user/queue/proctor-session";
    private static final String USER_ERRORS = "/user/queue/errors";
    private static final Pattern SESSION_TOPIC = Pattern.compile(
            "^/topic/proctor-sessions/([0-9a-fA-F-]{36})$");
    private static final Pattern OPEN_DESTINATION = Pattern.compile(
            "^/app/sessions/([0-9a-fA-F-]{36})/open$");
    private static final Pattern COMMAND_DESTINATION = Pattern.compile(
            "^/app/proctor-sessions/([0-9a-fA-F-]{36})/(commands|violations)$");

    private final ProctorSessionRepository repository;

    public StompDestinationAuthorizationService(ProctorSessionRepository repository) {
        this.repository = repository;
    }

    public void authorizeSubscribe(CurrentUser caller, String destination) {
        if (USER_PROCTOR_SESSION.equals(destination) || USER_ERRORS.equals(destination)) {
            return;
        }
        UUID sessionPublicId = parseSubscriptionSessionId(destination);
        UUID tenantId = caller.tenantId();
        if (tenantId == null) {
            throw new StompSubscriptionForbiddenException();
        }
        boolean allowed;
        if (caller.hasRole(ROLE_PROCTOR)) {
            allowed = repository.existsBySessionPublicIdAndProctorPublicIdAndTenantIdAndStatus(
                    sessionPublicId,
                    caller.userId(),
                    tenantId,
                    ProctorSessionStatus.ACTIVE);
        } else if (caller.hasRole(ROLE_HOST_ADMIN)) {
            allowed = repository.existsBySessionPublicIdAndTenantIdAndStatus(
                    sessionPublicId,
                    tenantId,
                    ProctorSessionStatus.ACTIVE);
        } else {
            allowed = false;
        }
        if (!allowed) {
            throw new StompSubscriptionForbiddenException();
        }
    }

    public void authorizeSend(CurrentUser caller, String destination) {
        if (caller.tenantId() == null || !caller.hasRole(ROLE_PROCTOR)) {
            throw new StompCommandForbiddenException();
        }
        if (matchesValidUuid(OPEN_DESTINATION, destination)
                || matchesValidUuid(COMMAND_DESTINATION, destination)) {
            return;
        }
        throw new StompCommandForbiddenException();
    }

    private UUID parseSubscriptionSessionId(String destination) {
        Matcher matcher = destination == null ? null : SESSION_TOPIC.matcher(destination);
        if (matcher == null || !matcher.matches()) {
            throw new StompSubscriptionForbiddenException();
        }
        try {
            return UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException exception) {
            throw new StompSubscriptionForbiddenException();
        }
    }

    private boolean matchesValidUuid(Pattern pattern, String destination) {
        Matcher matcher = destination == null ? null : pattern.matcher(destination);
        if (matcher == null || !matcher.matches()) {
            return false;
        }
        try {
            UUID.fromString(matcher.group(1));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
