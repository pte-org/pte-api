package com.aptis.modules.proctor.config;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.interfaces.StudentAttemptLookup;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.interfaces.JwtOperations;
import com.aptis.modules.examoperations.repository.ExamRepository;
import com.aptis.modules.proctor.service.ProctorWebSocketSessionRegistry;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

/**
 * Authenticates STOMP CONNECT frames using the existing JWT (no separate WebSocket auth
 * mechanism). Two connecting roles are allowed: PROCTOR (subscribes to
 * /user/{examId}/queue/attempt-status, verified against exam.proctorId) and STUDENT
 * (subscribes to /topic/exam/{examId}/announcements for broadcast, verified against having
 * an attempt for that exam). Every frame on a session revoked by logout is rejected.
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);

    private final JwtOperations jwtOperations;
    private final ExamRepository examRepository;
    private final ProctorWebSocketSessionRegistry sessionRegistry;
    private final StudentAttemptLookup studentAttemptLookup;

    public StompAuthChannelInterceptor(
            JwtOperations jwtOperations,
            ExamRepository examRepository,
            ProctorWebSocketSessionRegistry sessionRegistry,
            StudentAttemptLookup studentAttemptLookup) {
        this.jwtOperations = jwtOperations;
        this.examRepository = examRepository;
        this.sessionRegistry = sessionRegistry;
        this.studentAttemptLookup = studentAttemptLookup;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        String sessionId = accessor.getSessionId();

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            JwtPrincipal principal = authenticate(accessor);
            accessor.setUser(principal);
            if (sessionId != null) {
                sessionRegistry.register(sessionId, principal.userId());
            }
            return message;
        }

        if (sessionId != null && sessionRegistry.isRevoked(sessionId)) {
            LOGGER.warn("Rejected STOMP frame on revoked session {}", sessionId);
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "WebSocket session has been logged out");
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }

        if (StompCommand.DISCONNECT.equals(accessor.getCommand()) && sessionId != null) {
            sessionRegistry.unregister(sessionId);
        }

        return message;
    }

    private JwtPrincipal authenticate(StompHeaderAccessor accessor) {
        String authorizationHeader = accessor.getFirstNativeHeader(IamApiConstants.AUTHORIZATION_HEADER);
        if (authorizationHeader == null || !authorizationHeader.startsWith(IamApiConstants.BEARER_PREFIX)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Missing WebSocket Authorization header");
        }
        String token = authorizationHeader.substring(IamApiConstants.BEARER_PREFIX.length());
        try {
            Claims claims = jwtOperations.validateToken(token);
            JwtPrincipal principal = jwtOperations.extractPrincipal(claims);
            boolean isProctor = IamApiConstants.USER_TYPE_PROCTOR.equals(principal.userType());
            boolean isStudent = IamApiConstants.USER_TYPE_STUDENT.equals(principal.userType());
            if (!isProctor && !isStudent) {
                throw new ApiException(ErrorCode.ACCESS_DENIED, "Only proctors or students may connect to this endpoint");
            }
            return principal;
        } catch (JwtException | IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid WebSocket JWT");
        }
    }

    /**
     * Two destination shapes are governed here, deny-by-default for anything else (tightened
     * from Phase 7's "let it through" default, since students are now allowed to connect too
     * and an open default is riskier once the connecting population isn't proctor-only):
     * <ul>
     *   <li>{@code /user/{examId}/queue/attempt-status} — PROCTOR only, must be assigned.</li>
     *   <li>{@code /topic/exam/{examId}/announcements} — STUDENT only, must have an attempt
     *       for that exam.</li>
     * </ul>
     */
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Subscription destination is required");
        }

        Object userPrincipal = accessor.getUser();
        if (!(userPrincipal instanceof JwtPrincipal principal)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }

        Long proctorRoomExamId = parseExamIdFromDestination(destination, "user", "queue");
        if (proctorRoomExamId != null) {
            authorizeProctorRoom(principal, proctorRoomExamId);
            return;
        }

        Long announcementExamId = parseAnnouncementExamId(destination);
        if (announcementExamId != null) {
            authorizeStudentAnnouncements(principal, announcementExamId);
            return;
        }

        LOGGER.warn("Rejected subscription to ungoverned destination {}", destination);
        throw new ApiException(ErrorCode.ACCESS_DENIED, "Unknown or disallowed subscription destination");
    }

    private void authorizeProctorRoom(JwtPrincipal principal, Long examId) {
        boolean assigned = examRepository.findById(examId)
                .map(exam -> principal.userId().equals(exam.getProctorId()))
                .orElse(false);

        if (!assigned) {
            LOGGER.warn("Proctor {} rejected: not assigned to exam {}", principal.userId(), examId);
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Not assigned to this exam session");
        }
    }

    private void authorizeStudentAnnouncements(JwtPrincipal principal, Long examId) {
        if (!studentAttemptLookup.hasAttemptForExam(principal.userId(), examId)) {
            LOGGER.warn("Student {} rejected: no attempt for exam {}", principal.userId(), examId);
            throw new ApiException(ErrorCode.ACCESS_DENIED, "No attempt for this exam session");
        }
    }

    private Long parseExamIdFromDestination(String destination, String prefixSegment, String suffixSegment) {
        // Expected: /{prefixSegment}/{examId}/{suffixSegment}/...
        String[] segments = destination.split("/");
        if (segments.length < 4 || !prefixSegment.equals(segments[1]) || !suffixSegment.equals(segments[3])) {
            return null;
        }
        try {
            return Long.valueOf(segments[2]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long parseAnnouncementExamId(String destination) {
        // Expected: /topic/exam/{examId}/announcements
        String[] segments = destination.split("/");
        if (segments.length < 5 || !"topic".equals(segments[1]) || !"exam".equals(segments[2])
                || !"announcements".equals(segments[4])) {
            return null;
        }
        try {
            return Long.valueOf(segments[3]);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
