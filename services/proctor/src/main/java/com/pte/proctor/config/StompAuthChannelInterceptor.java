package com.pte.proctor.config;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.SecurityClaims;
import com.pte.proctor.domain.exception.StompCommandForbiddenException;
import com.pte.proctor.domain.exception.StompSubscriptionForbiddenException;
import com.pte.proctor.security.StompPrincipal;
import com.pte.proctor.service.StompDestinationAuthorizationService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Authenticates the STOMP {@code CONNECT} frame's {@code Authorization}
 * header against the same JWKS-backed decoder the REST resource-server chain
 * uses. Deliberately NOT done at the HTTP handshake: browser WebSocket clients
 * can't reliably set custom handshake headers, but every STOMP client can set
 * native frame headers uniformly. Throwing here rejects the CONNECT outright
 * (Spring closes the session with an ERROR frame) — every later frame on that
 * session inherits the {@link StompPrincipal} set on success.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder jwtDecoder;
    private final StompDestinationAuthorizationService authorizationService;

    public StompAuthChannelInterceptor(
            JwtDecoder jwtDecoder,
            StompDestinationAuthorizationService authorizationService) {
        this.jwtDecoder = jwtDecoder;
        this.authorizationService = authorizationService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            CurrentUser currentUser = authenticate(accessor.getFirstNativeHeader("Authorization"));
            accessor.setUser(new StompPrincipal(currentUser));
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizationService.authorizeSubscribe(
                    currentUser(accessor, true),
                    accessor.getDestination());
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            authorizationService.authorizeSend(
                    currentUser(accessor, false),
                    accessor.getDestination());
        }
        return MessageBuilder.createMessage(
                message.getPayload(),
                accessor.getMessageHeaders());
    }

    private CurrentUser currentUser(
            StompHeaderAccessor accessor,
            boolean subscription) {
        if (accessor.getUser() instanceof StompPrincipal principal) {
            return principal.currentUser();
        }
        if (subscription) {
            throw new StompSubscriptionForbiddenException();
        }
        throw new StompCommandForbiddenException();
    }

    private CurrentUser authenticate(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new IllegalArgumentException("Missing or malformed Authorization header on STOMP CONNECT");
        }
        try {
            Jwt jwt = jwtDecoder.decode(authHeader.substring(BEARER_PREFIX.length()));
            return toCurrentUser(jwt);
        } catch (JwtException ex) {
            throw new IllegalArgumentException("Invalid JWT on STOMP CONNECT", ex);
        }
    }

    private CurrentUser toCurrentUser(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        String tenantRaw = jwt.getClaimAsString(SecurityClaims.TENANT_ID);
        UUID tenantId = (tenantRaw == null || tenantRaw.isBlank()) ? null : UUID.fromString(tenantRaw);
        List<String> roles = jwt.getClaimAsStringList(SecurityClaims.ROLES);
        return new CurrentUser(userId, tenantId, roles == null ? List.of() : roles);
    }
}
