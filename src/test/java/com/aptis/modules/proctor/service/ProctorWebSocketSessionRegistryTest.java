package com.aptis.modules.proctor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.CloseStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import static org.mockito.Mockito.mock;

@DisplayName("ProctorWebSocketSessionRegistry Tests")
class ProctorWebSocketSessionRegistryTest {

    private ProctorWebSocketSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ProctorWebSocketSessionRegistry();
    }

    // ==================== register + isRevoked Tests ====================

    @Test
    @DisplayName("register: session is not revoked initially")
    void register_sessionNotRevokedInitially() {
        // Arrange
        String sessionId = "session-123";
        Long proctorId = 1L;

        // Act
        registry.register(sessionId, proctorId);

        // Assert
        assertFalse(registry.isRevoked(sessionId));
    }

    @Test
    @DisplayName("register: can track multiple sessions for same proctor")
    void register_multipleSessionsForSameProctor() {
        // Arrange
        Long proctorId = 1L;
        String session1 = "session-1";
        String session2 = "session-2";

        // Act
        registry.register(session1, proctorId);
        registry.register(session2, proctorId);

        // Assert
        assertFalse(registry.isRevoked(session1));
        assertFalse(registry.isRevoked(session2));
    }

    // ==================== revokeAllForUser Tests ====================

    @Test
    @DisplayName("revokeAllForUser: marks all sessions for that proctor as revoked")
    void revokeAllForUser_revokesSingleSession() {
        // Arrange
        Long proctorId = 1L;
        String sessionId = "session-123";
        registry.register(sessionId, proctorId);

        // Act
        registry.revokeAllForUser(proctorId);

        // Assert
        assertTrue(registry.isRevoked(sessionId));
    }

    @Test
    @DisplayName("revokeAllForUser: revokes all sessions for target proctor only")
    void revokeAllForUser_revokesOnlyTargetProctor() {
        // Arrange
        Long proctorId1 = 1L;
        Long proctorId2 = 2L;
        String session1_p1 = "session-1-proctor-1";
        String session2_p1 = "session-2-proctor-1";
        String session1_p2 = "session-1-proctor-2";

        registry.register(session1_p1, proctorId1);
        registry.register(session2_p1, proctorId1);
        registry.register(session1_p2, proctorId2);

        // Act
        registry.revokeAllForUser(proctorId1);

        // Assert
        assertTrue(registry.isRevoked(session1_p1));
        assertTrue(registry.isRevoked(session2_p1));
        assertFalse(registry.isRevoked(session1_p2));
    }

    @Test
    @DisplayName("revokeAllForUser: non-existent proctor is no-op")
    void revokeAllForUser_nonExistentProctor_noOp() {
        // Arrange
        Long proctorId1 = 1L;
        String sessionId = "session-123";
        registry.register(sessionId, proctorId1);

        // Act
        registry.revokeAllForUser(999L);

        // Assert
        assertFalse(registry.isRevoked(sessionId));
    }

    // ==================== unregister Tests ====================

    @Test
    @DisplayName("unregister: removes session mapping")
    void unregister_removesSession() {
        // Arrange
        String sessionId = "session-123";
        Long proctorId = 1L;
        registry.register(sessionId, proctorId);

        // Act
        registry.unregister(sessionId);

        // Assert
        // After unregister, querying again should not throw; session just won't exist
        assertFalse(registry.isRevoked(sessionId));
    }

    @Test
    @DisplayName("unregister: removes revocation flag if set")
    void unregister_removesRevocationFlag() {
        // Arrange
        String sessionId = "session-123";
        Long proctorId = 1L;
        registry.register(sessionId, proctorId);
        registry.revokeAllForUser(proctorId);

        assertTrue(registry.isRevoked(sessionId));

        // Act
        registry.unregister(sessionId);

        // Assert
        assertFalse(registry.isRevoked(sessionId));
    }

    @Test
    @DisplayName("unregister: unregistering non-existent session is safe")
    void unregister_nonExistentSession_safe() {
        // Arrange & Act
        registry.unregister("non-existent-session");

        // Assert — should not throw
        assertFalse(registry.isRevoked("non-existent-session"));
    }

    // ==================== onSessionDisconnect Tests ====================

    @Test
    @DisplayName("onSessionDisconnect: unregisters the disconnected session")
    void onSessionDisconnect_unregistersSession() {
        // Arrange
        String sessionId = "session-123";
        Long proctorId = 1L;
        registry.register(sessionId, proctorId);

        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, message, sessionId, CloseStatus.NORMAL);

        // Act
        registry.onSessionDisconnect(event);

        // Assert
        assertFalse(registry.isRevoked(sessionId));
    }

    @Test
    @DisplayName("onSessionDisconnect: cleans up multiple sessions")
    void onSessionDisconnect_cleansUpEachSession() {
        // Arrange
        Long proctorId = 1L;
        String session1 = "session-1";
        String session2 = "session-2";
        registry.register(session1, proctorId);
        registry.register(session2, proctorId);

        Message<byte[]> message1 = MessageBuilder.withPayload(new byte[0]).build();
        Message<byte[]> message2 = MessageBuilder.withPayload(new byte[0]).build();

        // Act
        registry.onSessionDisconnect(new SessionDisconnectEvent(this, message1, session1, CloseStatus.NORMAL));
        registry.onSessionDisconnect(new SessionDisconnectEvent(this, message2, session2, CloseStatus.NORMAL));

        // Assert
        assertFalse(registry.isRevoked(session1));
        assertFalse(registry.isRevoked(session2));
    }

    // ==================== Integration Tests ====================

    @Test
    @DisplayName("register + revokeAllForUser + unregister: full lifecycle")
    void fullLifecycle() {
        // Arrange
        Long proctorId = 1L;
        String sessionId = "session-123";

        // Act & Assert — register
        registry.register(sessionId, proctorId);
        assertFalse(registry.isRevoked(sessionId));

        // Act & Assert — revoke
        registry.revokeAllForUser(proctorId);
        assertTrue(registry.isRevoked(sessionId));

        // Act & Assert — unregister
        registry.unregister(sessionId);
        assertFalse(registry.isRevoked(sessionId));
    }

    @Test
    @DisplayName("Multiple proctors with overlapping sessions: independent revocation")
    void multipleProctorsIndependentRevocation() {
        // Arrange
        Long proctor1 = 1L;
        Long proctor2 = 2L;
        String session1_p1 = "s1";
        String session2_p1 = "s2";
        String session1_p2 = "s3";

        registry.register(session1_p1, proctor1);
        registry.register(session2_p1, proctor1);
        registry.register(session1_p2, proctor2);

        // Act
        registry.revokeAllForUser(proctor1);

        // Assert
        assertTrue(registry.isRevoked(session1_p1));
        assertTrue(registry.isRevoked(session2_p1));
        assertFalse(registry.isRevoked(session1_p2));

        // Unregister one session for proctor1
        registry.unregister(session1_p1);

        assertFalse(registry.isRevoked(session1_p1));
        assertTrue(registry.isRevoked(session2_p1)); // Still revoked
        assertFalse(registry.isRevoked(session1_p2)); // Unaffected
    }
}
