package com.aptis.modules.proctor.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Tracks which STOMP session ids belong to which authenticated proctor, and which sessions
 * have been revoked (e.g. by HTTP logout). The auth interceptor consults this on every
 * frame — not just CONNECT — so a logged-out proctor's still-open WebSocket connection
 * cannot keep sending/receiving after logout (Design Constraint: don't rely on HttpSession
 * for WebSocket state; this registry is the WebSocket-native equivalent).
 *
 * <p>Cleanup does not rely solely on a well-behaved client sending a STOMP DISCONNECT frame
 * (an abrupt network drop never sends one): Spring's WebSocket support publishes {@link
 * SessionDisconnectEvent} for every session teardown regardless of cause, so that is the
 * authoritative cleanup trigger this registry listens to.
 */
@Component
public class ProctorWebSocketSessionRegistry {

    private final ConcurrentHashMap<String, Long> proctorIdByStompSessionId = new ConcurrentHashMap<>();
    private final Set<String> revokedStompSessionIds = ConcurrentHashMap.newKeySet();

    public void register(String stompSessionId, Long proctorId) {
        proctorIdByStompSessionId.put(stompSessionId, proctorId);
    }

    public void unregister(String stompSessionId) {
        proctorIdByStompSessionId.remove(stompSessionId);
        revokedStompSessionIds.remove(stompSessionId);
    }

    /**
     * Authoritative cleanup path: fires for every session teardown (clean STOMP DISCONNECT,
     * abrupt network drop, client crash, transport error) — unlike relying on the interceptor
     * having seen an explicit DISCONNECT frame, which an abrupt drop never sends.
     */
    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        unregister(event.getSessionId());
    }

    public boolean isRevoked(String stompSessionId) {
        return revokedStompSessionIds.contains(stompSessionId);
    }

    /** Called on HTTP logout: marks every open STOMP session for this proctor as revoked. */
    public void revokeAllForUser(Long proctorId) {
        Set<String> toRevoke = new CopyOnWriteArraySet<>();
        proctorIdByStompSessionId.forEach((sessionId, id) -> {
            if (id.equals(proctorId)) {
                toRevoke.add(sessionId);
            }
        });
        revokedStompSessionIds.addAll(toRevoke);
    }
}
