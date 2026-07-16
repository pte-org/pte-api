package com.aptis.modules.proctor.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.interfaces.StudentAttemptLookup;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.interfaces.JwtOperations;
import com.aptis.modules.examoperations.domain.Exam;
import com.aptis.modules.examoperations.repository.ExamRepository;
import com.aptis.modules.proctor.service.ProctorWebSocketSessionRegistry;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

@ExtendWith(MockitoExtension.class)
@DisplayName("StompAuthChannelInterceptor Tests")
class StompAuthChannelInterceptorTest {

    @Mock
    private JwtOperations jwtOperations;

    @Mock
    private ExamRepository examRepository;

    @Mock
    private ProctorWebSocketSessionRegistry sessionRegistry;

    @Mock
    private StudentAttemptLookup studentAttemptLookup;

    private StompAuthChannelInterceptor interceptor;
    private JwtPrincipal proctorPrincipal;
    private JwtPrincipal studentPrincipal;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtOperations, examRepository, sessionRegistry, studentAttemptLookup);
        proctorPrincipal = new JwtPrincipal(1L, IamApiConstants.USER_TYPE_PROCTOR, IamApiConstants.ROLE_PROCTOR, 100L);
        studentPrincipal = new JwtPrincipal(2L, IamApiConstants.USER_TYPE_STUDENT, IamApiConstants.ROLE_STUDENT, 100L);
    }

    private Message<?> createStompMessage(StompCommand command, String sessionId, String destination, String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authHeader != null) {
            accessor.setNativeHeader(IamApiConstants.AUTHORIZATION_HEADER, authHeader);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    // ==================== CONNECT Frame Tests ====================

    @Test
    @DisplayName("CONNECT with valid proctor JWT: authenticates and registers session")
    void connect_withValidJwt_authenticatesAndRegisters() {
        // Arrange
        String validToken = "Bearer valid.jwt.token";
        String sessionId = "session-123";

        Message<?> message = createStompMessage(StompCommand.CONNECT, sessionId, null, validToken);

        Claims claims = Jwts.claims().build();
        when(jwtOperations.validateToken("valid.jwt.token")).thenReturn(claims);
        when(jwtOperations.extractPrincipal(claims)).thenReturn(proctorPrincipal);

        // Act
        Message<?> result = interceptor.preSend(message, null);

        // Assert
        assertThat(result).isNotNull();
        verify(jwtOperations).validateToken("valid.jwt.token");
        verify(jwtOperations).extractPrincipal(claims);
        verify(sessionRegistry).register(sessionId, proctorPrincipal.userId());
    }

    @Test
    @DisplayName("CONNECT with missing Authorization header: throws UNAUTHENTICATED")
    void connect_withMissingAuthHeader_throwsUnauthenticated() {
        // Arrange
        Message<?> message = createStompMessage(StompCommand.CONNECT, "session-123", null, null);

        // Act & Assert
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("CONNECT with malformed Authorization header: throws UNAUTHENTICATED")
    void connect_withMalformedAuthHeader_throwsUnauthenticated() {
        // Arrange
        Message<?> message = createStompMessage(StompCommand.CONNECT, "session-123", null, "InvalidFormat");

        // Act & Assert
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("CONNECT with invalid JWT: throws UNAUTHENTICATED")
    void connect_withInvalidJwt_throwsUnauthenticated() {
        // Arrange
        String invalidToken = "Bearer invalid.jwt.token";
        Message<?> message = createStompMessage(StompCommand.CONNECT, "session-123", null, invalidToken);

        when(jwtOperations.validateToken("invalid.jwt.token"))
                .thenThrow(new io.jsonwebtoken.JwtException("Invalid JWT"));

        // Act & Assert
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }

    // ==================== SUBSCRIBE Frame Tests ====================

    @Test
    @DisplayName("SUBSCRIBE to assigned exam: authorizes subscription")
    void subscribe_toAssignedExam_succeeds() {
        // Arrange
        Long examId = 100L;
        String destination = "/user/" + examId + "/queue/attempt-status";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-123");
        accessor.setDestination(destination);
        accessor.setUser(proctorPrincipal);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Exam exam = mock(Exam.class);
        when(exam.getProctorId()).thenReturn(proctorPrincipal.userId());
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        // Act
        Message<?> result = interceptor.preSend(message, null);

        // Assert
        assertThat(result).isNotNull();
        verify(examRepository).findById(examId);
    }

    @Test
    @DisplayName("SUBSCRIBE to unassigned exam: throws ACCESS_DENIED")
    void subscribe_toUnassignedExam_throwsAccessDenied() {
        // Arrange
        Long examId = 100L;
        Long differentProctorId = 999L;
        String destination = "/user/" + examId + "/queue/attempt-status";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-123");
        accessor.setDestination(destination);
        accessor.setUser(proctorPrincipal);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Exam exam = mock(Exam.class);
        when(exam.getProctorId()).thenReturn(differentProctorId);
        when(examRepository.findById(examId)).thenReturn(Optional.of(exam));

        // Act & Assert
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("SUBSCRIBE to non-existent exam: throws ACCESS_DENIED")
    void subscribe_toNonExistentExam_throwsAccessDenied() {
        // Arrange
        Long examId = 999L;
        String destination = "/user/" + examId + "/queue/attempt-status";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-123");
        accessor.setDestination(destination);
        accessor.setUser(proctorPrincipal);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(examRepository.findById(examId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("SUBSCRIBE with null destination: throws VALIDATION_ERROR")
    void subscribe_withNullDestination_throwsValidationError() {
        // Arrange
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-123");
        accessor.setDestination(null);
        accessor.setUser(proctorPrincipal);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // Act & Assert
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("SUBSCRIBE to non-scoped destination: rejects (deny-by-default, Phase 8)")
    void subscribe_toNonScopedDestination_throwsAccessDenied() {
        // Arrange
        String destination = "/queue/notifications"; // Not /user/{examId}/...

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-123");
        accessor.setDestination(destination);
        accessor.setUser(proctorPrincipal);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // Act & Assert — Phase 8 deny-by-default: unrecognized destinations are rejected
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

        verify(examRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    // ==================== Revoked Session Tests ====================

    @Test
    @DisplayName("SUBSCRIBE on revoked session: throws UNAUTHENTICATED")
    void subscribe_onRevokedSession_throwsUnauthenticated() {
        // Arrange
        String sessionId = "revoked-session-123";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setDestination("/user/100/queue/attempt-status");
        accessor.setUser(proctorPrincipal);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(sessionRegistry.isRevoked(sessionId)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("SEND on revoked session: throws UNAUTHENTICATED")
    void send_onRevokedSession_throwsUnauthenticated() {
        // Arrange
        String sessionId = "revoked-session-123";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setSessionId(sessionId);
        accessor.setUser(proctorPrincipal);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(sessionRegistry.isRevoked(sessionId)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.UNAUTHENTICATED);
    }

    // ==================== DISCONNECT Frame Tests ====================

    @Test
    @DisplayName("DISCONNECT: unregisters session")
    void disconnect_unregistersSession() {
        // Arrange
        String sessionId = "session-123";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(sessionId);
        accessor.setUser(proctorPrincipal);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // Act
        Message<?> result = interceptor.preSend(message, null);

        // Assert
        assertThat(result).isNotNull();
        verify(sessionRegistry).unregister(sessionId);
    }

    @Test
    @DisplayName("DISCONNECT with no session ID: no-op")
    void disconnect_withNoSessionId_noOp() {
        // Arrange
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setSessionId(null);
        accessor.setUser(proctorPrincipal);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // Act
        Message<?> result = interceptor.preSend(message, null);

        // Assert
        assertThat(result).isNotNull();
        verify(sessionRegistry, never()).unregister(anyString());
    }

    @Test
    @DisplayName("Non-STOMP command message: passes through")
    void nonStompCommand_passesThrough() {
        // Arrange
        Message<?> message = MessageBuilder.withPayload("test").build();

        // Act
        Message<?> result = interceptor.preSend(message, null);

        // Assert
        assertThat(result).isNotNull();
    }

    // ==================== Phase 8 Tests: STUDENT Support ====================

    @Test
    @DisplayName("CONNECT with valid STUDENT JWT: authenticates (Phase 8 now allows STUDENT)")
    void connect_withStudentJwt_authenticates() {
        // Arrange
        String validToken = "Bearer valid.jwt.token";
        String sessionId = "session-student-123";

        Message<?> message = createStompMessage(StompCommand.CONNECT, sessionId, null, validToken);

        Claims claims = Jwts.claims().build();
        when(jwtOperations.validateToken("valid.jwt.token")).thenReturn(claims);
        when(jwtOperations.extractPrincipal(claims)).thenReturn(studentPrincipal);

        // Act
        Message<?> result = interceptor.preSend(message, null);

        // Assert
        assertThat(result).isNotNull();
        verify(jwtOperations).validateToken("valid.jwt.token");
        verify(jwtOperations).extractPrincipal(claims);
        verify(sessionRegistry).register(sessionId, studentPrincipal.userId());
    }

    @Test
    @DisplayName("CONNECT with HOST role (neither PROCTOR nor STUDENT): throws ACCESS_DENIED")
    void connect_withHostRole_throwsAccessDenied() {
        // Arrange
        String validToken = "Bearer valid.jwt.token";
        JwtPrincipal hostPrincipal = new JwtPrincipal(3L, "HOST", "ROLE_HOST", 100L);

        Message<?> message = createStompMessage(StompCommand.CONNECT, "session-host-123", null, validToken);

        Claims claims = Jwts.claims().build();
        when(jwtOperations.validateToken("valid.jwt.token")).thenReturn(claims);
        when(jwtOperations.extractPrincipal(claims)).thenReturn(hostPrincipal);

        // Act & Assert
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    // ==================== Phase 8 Tests: STUDENT Broadcast Subscription ====================

    @Test
    @DisplayName("SUBSCRIBE to /topic/exam/{examId}/announcements by STUDENT with attempt: authorizes")
    void subscribe_toBroadcastDestination_studentWithAttempt_succeeds() {
        // Arrange
        Long examId = 100L;
        String destination = "/topic/exam/" + examId + "/announcements";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-123");
        accessor.setDestination(destination);
        accessor.setUser(studentPrincipal);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(studentAttemptLookup.hasAttemptForExam(studentPrincipal.userId(), examId)).thenReturn(true);

        // Act
        Message<?> result = interceptor.preSend(message, null);

        // Assert
        assertThat(result).isNotNull();
        verify(studentAttemptLookup).hasAttemptForExam(studentPrincipal.userId(), examId);
    }

    @Test
    @DisplayName("SUBSCRIBE to /topic/exam/{examId}/announcements by STUDENT without attempt: throws ACCESS_DENIED")
    void subscribe_toBroadcastDestination_studentWithoutAttempt_throwsAccessDenied() {
        // Arrange
        Long examId = 100L;
        String destination = "/topic/exam/" + examId + "/announcements";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-123");
        accessor.setDestination(destination);
        accessor.setUser(studentPrincipal);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(studentAttemptLookup.hasAttemptForExam(studentPrincipal.userId(), examId)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("SUBSCRIBE to unrecognized destination: deny-by-default, throws ACCESS_DENIED")
    void subscribe_toUnrecognizedDestination_throwsAccessDenied() {
        // Arrange
        String destination = "/topic/random/nonsense";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-123");
        accessor.setDestination(destination);
        accessor.setUser(proctorPrincipal);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // Act & Assert
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

        verify(examRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
        verify(studentAttemptLookup, never()).hasAttemptForExam(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("SUBSCRIBE to /queue/notifications (non-scoped): rejects (deny-by-default)")
    void subscribe_toNonScopedQueueDestination_throwsAccessDenied() {
        // Arrange
        String destination = "/queue/notifications";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("session-123");
        accessor.setDestination(destination);
        accessor.setUser(studentPrincipal);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // Act & Assert — Phase 8 deny-by-default: unrecognized destinations are rejected
        assertThatThrownBy(() -> interceptor.preSend(message, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACCESS_DENIED);

        verify(examRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
        verify(studentAttemptLookup, never()).hasAttemptForExam(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }
}
