package com.pte.proctoring.internal.config;

import com.pte.identity.IdentityService;
import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.proctoring.internal.security.StompPrincipal;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityClaims;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Inbound STOMP contract enforcement:
 * Authenticates CONNECT frames with JWT/expiry and enforces strict destination allowlists
 * and real-time assignment checks on SUBSCRIBE and SEND.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOPIC_PREFIX = "/topic/proctor-sessions/";
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final Set<String> ALLOWED_USER_QUEUES = Set.of(
            "/user/queue/proctor-session",
            "/user/queue/errors",
            "/user/queue/proctor-results",
            "/user/queue/proctor-errors"
    );

    private final JwtDecoder jwtDecoder;
    private final ProctorConnectionRegistry connectionRegistry;
    private final SessionService sessionService;
    private final IdentityService identityService;

    public StompAuthChannelInterceptor(
            JwtDecoder jwtDecoder,
            ProctorConnectionRegistry connectionRegistry,
            SessionService sessionService,
            IdentityService identityService) {
        this.jwtDecoder = jwtDecoder;
        this.connectionRegistry = connectionRegistry;
        this.sessionService = sessionService;
        this.identityService = identityService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            accessor = StompHeaderAccessor.wrap(message);
        }
        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            handleSubscribe(accessor);
        } else if (StompCommand.SEND.equals(command)) {
            handleSend(accessor);
        } else if (StompCommand.UNSUBSCRIBE.equals(command)) {
            handleUnsubscribe(accessor);
        } else if (StompCommand.DISCONNECT.equals(command)) {
            handleDisconnect(accessor);
        }

        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new IllegalArgumentException(ProctorConstants.STOMP_AUTH_HEADER_MISSING);
        }
        try {
            Jwt jwt = jwtDecoder.decode(authHeader.substring(BEARER_PREFIX.length()));
            CurrentUser currentUser = toCurrentUser(jwt);
            Instant expiresAt = jwt.getExpiresAt();
            accessor.setUser(new StompPrincipal(currentUser));
            connectionRegistry.register(accessor.getSessionId(), currentUser, expiresAt);
        } catch (JwtException ex) {
            throw new IllegalArgumentException(ProctorConstants.STOMP_JWT_INVALID, ex);
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Destination required for SUBSCRIBE");
        }

        CurrentUser caller = extractCurrentUser(accessor.getUser(), accessor.getSessionId());
        if (destination.startsWith(TOPIC_PREFIX)) {
            String uuidStr = destination.substring(TOPIC_PREFIX.length());
            if (!UUID_PATTERN.matcher(uuidStr).matches()) {
                throw new IllegalArgumentException("Invalid destination format");
            }
            UUID sessionPublicId = UUID.fromString(uuidStr);

            if (!caller.hasRole("PROCTOR")) {
                throw new IllegalArgumentException("PROCTOR role required for monitoring subscription");
            }
            if (caller.tenantId() == null || !identityService.isActiveProctor(caller.userId(), caller.tenantId())) {
                throw new IllegalArgumentException("Active PROCTOR account required");
            }
            if (!sessionService.isProctorAssignedToSession(sessionPublicId, caller.userId(), caller.tenantId())) {
                throw new IllegalArgumentException("Proctor is not assigned to this exam session");
            }

            connectionRegistry.addSubscription(accessor.getSessionId(), accessor.getSubscriptionId(), destination);
        } else if (ALLOWED_USER_QUEUES.contains(destination)) {
            connectionRegistry.addSubscription(accessor.getSessionId(), accessor.getSubscriptionId(), destination);
        } else {
            throw new IllegalArgumentException("Destination not allowed for SUBSCRIBE: " + destination);
        }
    }

    private void handleSend(StompHeaderAccessor accessor) {
        if (accessor.getNativeHeader("simpSessionId") != null
                || accessor.getNativeHeader("simpUser") != null
                || accessor.getNativeHeader("sessionId") != null) {
            throw new IllegalArgumentException("Spoofed transport headers not permitted");
        }

        List<String> requestIds = accessor.getNativeHeader("requestId");
        if (requestIds != null && !requestIds.isEmpty()) {
            if (requestIds.size() > 1) {
                throw new IllegalArgumentException("Duplicate requestId header");
            }
            String reqId = requestIds.get(0);
            if (reqId == null || !UUID_PATTERN.matcher(reqId).matches()) {
                throw new IllegalArgumentException("Invalid requestId header format");
            }
        }

        String destination = accessor.getDestination();
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("Destination required for SEND");
        }

        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            throw new IllegalArgumentException("Session ID required for SEND");
        }
        var connOpt = connectionRegistry.getConnection(sessionId);
        if (connOpt.isEmpty()) {
            throw new IllegalArgumentException("Unregistered transport session");
        }
        var conn = connOpt.get();
        if (conn.isExpired(Instant.now())) {
            throw new IllegalArgumentException("Transport session token expired");
        }

        CurrentUser caller = extractCurrentUser(accessor.getUser(), sessionId);
        if (!caller.hasRole("PROCTOR")) {
            throw new IllegalArgumentException("PROCTOR role required for SEND commands");
        }
        if (caller.tenantId() == null || !identityService.isActiveProctor(caller.userId(), caller.tenantId())) {
            throw new IllegalArgumentException("Active PROCTOR membership required for SEND");
        }

        if (destination.startsWith("/app/sessions/") && destination.endsWith("/open")) {
            String mid = destination.substring("/app/sessions/".length(), destination.length() - "/open".length());
            if (!UUID_PATTERN.matcher(mid).matches()) {
                throw new IllegalArgumentException("Invalid open destination format");
            }
        } else if (destination.startsWith("/app/proctor-sessions/") && destination.endsWith("/commands")) {
            String mid = destination.substring("/app/proctor-sessions/".length(), destination.length() - "/commands".length());
            if (!UUID_PATTERN.matcher(mid).matches()) {
                throw new IllegalArgumentException("Invalid commands destination format");
            }
        } else if (destination.startsWith("/app/proctor-sessions/") && destination.endsWith("/violations")) {
            String mid = destination.substring("/app/proctor-sessions/".length(), destination.length() - "/violations".length());
            if (!UUID_PATTERN.matcher(mid).matches()) {
                throw new IllegalArgumentException("Invalid violations destination format");
            }
        } else {
            throw new IllegalArgumentException("Direct SEND destination not permitted: " + destination);
        }
    }

    private void handleUnsubscribe(StompHeaderAccessor accessor) {
        connectionRegistry.removeSubscription(accessor.getSessionId(), accessor.getSubscriptionId());
    }

    private void handleDisconnect(StompHeaderAccessor accessor) {
        connectionRegistry.unregister(accessor.getSessionId());
    }

    private CurrentUser extractCurrentUser(Principal principal, String sessionId) {
        if (principal instanceof StompPrincipal sp) {
            return sp.currentUser();
        }
        if (sessionId != null) {
            var conn = connectionRegistry.getConnection(sessionId);
            if (conn.isPresent()) {
                return conn.get().currentUser();
            }
        }
        throw new IllegalStateException("Unauthenticated STOMP user");
    }

    private CurrentUser toCurrentUser(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String tenantRaw = jwt.getClaimAsString(SecurityClaims.TENANT_ID);
        UUID tenantId = (tenantRaw == null || tenantRaw.isBlank()) ? null : UUID.fromString(tenantRaw);
        List<String> roles = jwt.getClaimAsStringList(SecurityClaims.ROLES);
        return new CurrentUser(userId, tenantId, roles == null ? List.of() : roles);
    }
}
