package com.pte.proctoring.internal.config;

import com.pte.identity.IdentityService;
import com.pte.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Recipient-specific authorization interceptor on clientOutboundChannel executing
 * immediately before socket handler. Rechecks proctor role, tenant, and active assignment
 * with a fresh database read.
 */
@Component
public class ProctorOutboundAuthorizationInterceptor implements ExecutorChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ProctorOutboundAuthorizationInterceptor.class);
    private static final String TOPIC_PREFIX = "/topic/proctor-sessions/";

    private final ProctorConnectionRegistry registry;
    private final SessionService sessionService;
    private final IdentityService identityService;
    private final Clock clock;

    public ProctorOutboundAuthorizationInterceptor(
            ProctorConnectionRegistry registry,
            SessionService sessionService,
            IdentityService identityService,
            Clock clock) {
        this.registry = registry;
        this.sessionService = sessionService;
        this.identityService = identityService;
        this.clock = clock;
    }

    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        boolean isMessage = SimpMessageType.MESSAGE.equals(accessor.getMessageType())
                || StompCommand.MESSAGE.equals(accessor.getCommand());
        if (!isMessage) {
            return message;
        }

        String sessionId = accessor.getSessionId();
        if (sessionId == null) {
            return message;
        }

        var connOpt = registry.getConnection(sessionId);
        if (connOpt.isEmpty()) {
            log.warn("Denied outbound message to unregistered transport session: {}", sessionId);
            return null;
        }

        var conn = connOpt.get();
        if (conn.isExpired(clock.instant())) {
            log.warn("Denied outbound message to expired session: {}", sessionId);
            return null;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        // Check topic broadcasts
        if (destination.startsWith(TOPIC_PREFIX)) {
            String sessionPublicIdStr = destination.substring(TOPIC_PREFIX.length());
            try {
                UUID sessionPublicId = UUID.fromString(sessionPublicIdStr);
                UUID userId = conn.currentUser().userId();
                UUID tenantId = conn.currentUser().tenantId();

                if (tenantId == null || !identityService.isActiveProctor(userId, tenantId)) {
                    log.warn("Denied outbound broadcast to non-active proctor: {}", userId);
                    return null;
                }

                if (!sessionService.isProctorAssignedToSession(sessionPublicId, userId, tenantId)) {
                    log.warn("Denied outbound broadcast to unassigned/revoked proctor: {}", userId);
                    return null;
                }
            } catch (IllegalArgumentException e) {
                log.warn("Malformed destination UUID on outbound topic: {}", destination);
                return null;
            }
        }

        return message;
    }
}
