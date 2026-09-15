package com.pte.proctoring.internal.controller;

import com.pte.shared.constant.SharedConstants;
import com.pte.proctoring.internal.exception.ProctorRoleRequiredException;
import com.pte.proctoring.internal.dto.request.FlagViolationRequest;
import com.pte.proctoring.internal.dto.request.IssueCommandRequest;
import com.pte.proctoring.internal.dto.response.ErrorResponse;
import com.pte.proctoring.internal.dto.response.ProctorSessionResponse;
import com.pte.proctoring.internal.security.StompPrincipal;
import com.pte.proctoring.internal.service.ProctorCommandService;
import com.pte.proctoring.internal.service.ProctorSessionService;
import com.pte.proctoring.internal.service.ViolationService;
import com.pte.shared.exception.DomainException;
import com.pte.shared.security.CurrentUser;
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
 * The primary command surface. {@code sessions/{sessionPublicId}/open}
 * addresses session's exam session (all a client knows before opening);
 * every later frame addresses the resulting {@code ProctorSession}'s own
 * publicId.
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
     * enforcement point.
     */
    private CurrentUser currentUser(Principal principal) {
        if (!(principal instanceof StompPrincipal stompPrincipal)) {
        throw new IllegalStateException(SharedConstants.NO_AUTHENTICATED_STOMP_PRINCIPAL);
        }
        CurrentUser currentUser = stompPrincipal.currentUser();
        if (!currentUser.hasRole(ROLE_PROCTOR)) {
            throw new ProctorRoleRequiredException();
        }
        return currentUser;
    }
}
