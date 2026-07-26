package com.pte.proctor.controller;

import com.pte.common.exception.DomainException;
import com.pte.common.security.CurrentUser;
import com.pte.proctor.domain.exception.ProctorRoleRequiredException;
import com.pte.proctor.dto.request.FlagViolationRequest;
import com.pte.proctor.dto.request.IssueCommandRequest;
import com.pte.proctor.dto.response.ErrorResponse;
import com.pte.proctor.dto.response.ProctorSessionResponse;
import com.pte.proctor.security.StompPrincipal;
import com.pte.proctor.service.ProctorCommandService;
import com.pte.proctor.service.ProctorSessionService;
import com.pte.proctor.service.ViolationService;
import jakarta.validation.Valid;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.security.Principal;
import java.util.UUID;

/**
 * The primary command surface (plan.md: "ProctorSession (WebSocket/STOMP)").
 * {@code sessions/{sessionPublicId}/open} addresses scheduling's exam session
 * (all a client knows before opening); every later frame addresses the
 * resulting {@code ProctorSession}'s own publicId.
 */
@Controller
public class ProctorStompController {

    private static final String ROLE_PROCTOR = "PROCTOR";

    private final ProctorSessionService proctorSessionService;
    private final ProctorCommandService proctorCommandService;
    private final ViolationService violationService;

    public ProctorStompController(ProctorSessionService proctorSessionService, ProctorCommandService proctorCommandService,
                                  ViolationService violationService) {
        this.proctorSessionService = proctorSessionService;
        this.proctorCommandService = proctorCommandService;
        this.violationService = violationService;
    }

    @MessageMapping("/sessions/{sessionPublicId}/open")
    @SendToUser("/queue/proctor-session")
    public ProctorSessionResponse open(@DestinationVariable UUID sessionPublicId, Principal principal) {
        return proctorSessionService.open(sessionPublicId, currentUser(principal));
    }

    @MessageMapping("/proctor-sessions/{proctorSessionPublicId}/commands")
    public void issueCommand(@DestinationVariable UUID proctorSessionPublicId,
                             @Valid @Payload IssueCommandRequest request, Principal principal) {
        proctorCommandService.issueCommand(proctorSessionPublicId, request, currentUser(principal));
    }

    @MessageMapping("/proctor-sessions/{proctorSessionPublicId}/violations")
    public void flagViolation(@DestinationVariable UUID proctorSessionPublicId,
                              @Valid @Payload FlagViolationRequest request, Principal principal) {
        violationService.flag(proctorSessionPublicId, request, currentUser(principal));
    }

    @MessageExceptionHandler(DomainException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleDomainException(DomainException ex) {
        return new ErrorResponse(ex.getStatus().value(), ex.getMessage());
    }

    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    @SendToUser("/queue/errors")
    public ErrorResponse handleValidationException() {
        return new ErrorResponse(400, "VALIDATION_FAILED");
    }

    /**
     * {@code @PreAuthorize} does not enforce on {@code @MessageMapping} methods
     * under this module's STOMP config, so role is checked here explicitly —
     * every handler routes through this one method, so it's the single
     * enforcement point (QUAL-002).
     */
    private CurrentUser currentUser(Principal principal) {
        if (!(principal instanceof StompPrincipal stompPrincipal)) {
            throw new IllegalStateException("No authenticated STOMP principal");
        }
        CurrentUser currentUser = stompPrincipal.currentUser();
        if (!currentUser.hasRole(ROLE_PROCTOR)) {
            throw new ProctorRoleRequiredException();
        }
        return currentUser;
    }
}
