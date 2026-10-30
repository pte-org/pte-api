package com.pte.proctoring.internal.config;

import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory registry of authenticated STOMP transport connections and their registered subscriptions.
 */
@Component
public class ProctorConnectionRegistry {

    public record ConnectionInfo(
            String sessionId,
            CurrentUser currentUser,
            Instant expiresAt,
            Instant connectedAt,
            Map<String, String> subscriptions
    ) {
        public boolean isExpired(Instant now) {
            return expiresAt != null && now.isAfter(expiresAt);
        }
    }

    private final Map<String, ConnectionInfo> connections = new ConcurrentHashMap<>();

    private final Clock clock;

    public ProctorConnectionRegistry(Clock clock) {
        this.clock = clock;
    }

    public void register(String sessionId, CurrentUser user, Instant expiresAt) {
        if (sessionId == null) return;
        connections.put(sessionId, new ConnectionInfo(
                sessionId, user, expiresAt, clock.instant(), new ConcurrentHashMap<>()));
    }

    public void addSubscription(String sessionId, String subscriptionId, String destination) {
        ConnectionInfo conn = connections.get(sessionId);
        if (conn != null && subscriptionId != null && destination != null) {
            conn.subscriptions().put(subscriptionId, destination);
        }
    }

    public void removeSubscription(String sessionId, String subscriptionId) {
        ConnectionInfo conn = connections.get(sessionId);
        if (conn != null && subscriptionId != null) {
            conn.subscriptions().remove(subscriptionId);
        }
    }

    public void unregister(String sessionId) {
        if (sessionId != null) {
            connections.remove(sessionId);
        }
    }

    public Optional<ConnectionInfo> getConnection(String sessionId) {
        if (sessionId == null) return Optional.empty();
        return Optional.ofNullable(connections.get(sessionId));
    }

    public boolean isRegistered(String sessionId) {
        return sessionId != null && connections.containsKey(sessionId);
    }
}
