package com.pte.proctoring.internal.controller;

import com.pte.shared.constant.SharedConstants;
import com.pte.proctoring.internal.exception.ProctorRoleRequiredException;
import com.pte.proctoring.internal.dto.request.FlagViolationRequest;
import com.pte.proctoring.internal.dto.request.IssueCommandRequest;
import com.pte.proctoring.internal.dto.response.ErrorResponse;
import com.pte.proctoring.internal.dto.response.ProctorOperationErrorResponse;
import com.pte.proctoring.internal.dto.response.ProctorOperationResultResponse;
import com.pte.proctoring.internal.dto.response.ProctorSessionResponse;
import com.pte.proctoring.internal.dto.response.ViolationEventResponse;
import com.pte.proctoring.internal.security.StompPrincipal;
import com.pte.proctoring.internal.service.ProctorCommandDispatchService;
import com.pte.proctoring.internal.service.ProctorWatchService;
import com.pte.proctoring.internal.service.ViolationService;
import com.pte.shared.exception.DomainException;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;
import jakarta.validation.Valid;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

/**
 * The primary command surface. Every frame addresses the exam session: the
 * "proctor session" a client gets back from {@code open} carries the exam
 * session id, so the {@code /proctor-sessions/{id}/...} paths are unchanged.
 */
@Controller
public class ProctorStompController {

    private final ProctorWatchService watchService;
    private final ProctorCommandDispatchService proctorCommandService;
    private final ViolationService violationService;
    private final SimpMessagingTemplate messagingTemplate;

    public ProctorStompController(ProctorWatchService watchService,
                                  ProctorCommandDispatchService proctorCommandService,
                                  ViolationService violationService,
                                  SimpMessagingTemplate messagingTemplate) {
        this.watchService = watchService;
        this.proctorCommandService = proctorCommandService;
        this.violationService = violationService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/sessions/{sessionPublicId}/open")
    public void open(
            @DestinationVariable UUID sessionPublicId,
            Principal principal,
            @Header(name = "requestId", required = false) String requestId,
            SimpMessageHeaderAccessor headerAccessor) {
        CurrentUser caller = currentUser(principal);
        ProctorSessionResponse response = watchService.open(sessionPublicId, caller);

        String simpSessionId = headerAccessor != null ? headerAccessor.getSessionId() : null;
        if (simpSessionId != null && principal != null) {
            sendToSocket(simpSessionId, principal.getName(), "/queue/proctor-session", response, requestId);

            if (requestId != null) {
                ProctorOperationResultResponse result = new ProctorOperationResultResponse(
                        requestId,
                        "OPEN",
                        sessionPublicId,
                        "OPENED",
                        response.publicId(),
                        Instant.now()
                );
                sendToSocket(simpSessionId, principal.getName(), "/queue/proctor-results", result, requestId);
            }
        }
    }

    @MessageMapping("/proctor-sessions/{sessionPublicId}/commands")
    public void issueCommand(
            @DestinationVariable UUID sessionPublicId,
            @Valid @Payload IssueCommandRequest request,
            Principal principal,
            @Header(name = "requestId", required = false) String requestId,
            SimpMessageHeaderAccessor headerAccessor) {
        CurrentUser caller = currentUser(principal);
        boolean applied = proctorCommandService.issueCommand(sessionPublicId, request, caller);

        String simpSessionId = headerAccessor != null ? headerAccessor.getSessionId() : null;
        if (requestId != null && simpSessionId != null) {
            ProctorOperationResultResponse result = new ProctorOperationResultResponse(
                    requestId,
                    "FORCE_SUBMIT",
                    request.attemptPublicId(),
                    applied ? "APPLIED" : "NO_OP",
                    null,
                    Instant.now()
            );
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sendToSocket(simpSessionId, principal.getName(), "/queue/proctor-results", result);
                    }
                });
            } else {
                sendToSocket(simpSessionId, principal.getName(), "/queue/proctor-results", result);
            }
        }
    }

    @MessageMapping("/proctor-sessions/{sessionPublicId}/violations")
    public void flagViolation(
            @DestinationVariable UUID sessionPublicId,
            @Valid @Payload FlagViolationRequest request,
            Principal principal,
            @Header(name = "requestId", required = false) String requestId,
            SimpMessageHeaderAccessor headerAccessor) {
        CurrentUser caller = currentUser(principal);
        ViolationEventResponse response = violationService.flag(sessionPublicId, request, caller);

        String simpSessionId = headerAccessor != null ? headerAccessor.getSessionId() : null;
        if (requestId != null && simpSessionId != null) {
            ProctorOperationResultResponse result = new ProctorOperationResultResponse(
                    requestId,
                    "FLAG_VIOLATION",
                    request.attemptPublicId(),
                    "SAVED",
                    response.publicId(),
                    response.detectedAt()
            );
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        sendToSocket(simpSessionId, principal.getName(), "/queue/proctor-results", result);
                    }
                });
            } else {
                sendToSocket(simpSessionId, principal.getName(), "/queue/proctor-results", result);
            }
        }
    }

    @MessageExceptionHandler(DomainException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleDomainException(DomainException ex, SimpMessageHeaderAccessor headerAccessor, Principal principal) {
        notifyErrorIfRequested(headerAccessor, principal, ex.getStatus().value(), ex.getMessage());
        return new ErrorResponse(ex.getStatus().value(), ex.getMessage());
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleValidationException(MethodArgumentNotValidException ex, SimpMessageHeaderAccessor headerAccessor, Principal principal) {
        notifyErrorIfRequested(headerAccessor, principal, 400, "VALIDATION_FAILED");
        return new ErrorResponse(400, "VALIDATION_FAILED");
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleGeneralException(Exception ex, SimpMessageHeaderAccessor headerAccessor, Principal principal) {
        notifyErrorIfRequested(headerAccessor, principal, 500, "INTERNAL_ERROR");
        return new ErrorResponse(500, "INTERNAL_ERROR");
    }

    private void notifyErrorIfRequested(SimpMessageHeaderAccessor headerAccessor, Principal principal, int status, String code) {
        if (headerAccessor == null) {
            return;
        }
        String requestId = headerAccessor.getFirstNativeHeader("requestId");
        String simpSessionId = headerAccessor.getSessionId();
        if (requestId == null || simpSessionId == null) {
            return;
        }

        String operation = "UNKNOWN";
        String destination = headerAccessor.getDestination();
        if (destination != null) {
            if (destination.contains("/open")) {
                operation = "OPEN";
            } else if (destination.contains("/commands")) {
                operation = "FORCE_SUBMIT";
            } else if (destination.contains("/violations")) {
                operation = "FLAG_VIOLATION";
            }
        }

        String principalName = principal != null ? principal.getName() : null;
        if (principalName != null) {
            ProctorOperationErrorResponse errorResponse = new ProctorOperationErrorResponse(
                    requestId,
                    operation,
                    null,
                    status,
                    code
            );
            sendToSocket(simpSessionId, principalName, "/queue/proctor-errors", errorResponse);
        }
    }

    private void sendToSocket(String sessionId, String user, String destination, Object payload) {
        sendToSocket(sessionId, user, destination, payload, null);
    }

    private void sendToSocket(String sessionId, String user, String destination, Object payload, String requestId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setSessionId(sessionId);
        if (requestId != null) {
            accessor.setNativeHeader("requestId", requestId);
        }
        accessor.setLeaveMutable(true);
        messagingTemplate.convertAndSendToUser(user, destination, payload, accessor.getMessageHeaders());
    }

    /**
     * {@code @PreAuthorize} does not enforce on {@code @MessageMapping} methods
     * under this module's STOMP config, so role is checked here explicitly —
     * every handler routes through this one method, so it's the single
     * enforcement point.
     */
    private CurrentUser currentUser(Principal principal) {
        if (!(principal instanceof StompPrincipal stompPrincipal)) {
            throw new IllegalStateException(SharedConstants.NO_AUTHENTICATED_STOMP_PRINCIPAL);
        }
        CurrentUser currentUser = stompPrincipal.currentUser();
        if (!currentUser.hasRole(SecurityRoles.PROCTOR)) {
            throw new ProctorRoleRequiredException();
        }
        return currentUser;
    }
}
