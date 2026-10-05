package com.pte.session.internal.controller;

import com.pte.session.SessionService;
import com.pte.session.internal.dto.response.SessionResolveResponse;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/exam-sessions")
@PreAuthorize("hasRole('STUDENT')")
public class StudentSessionController {

    private final SessionService sessionService;

    public StudentSessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * {@code code} is optional at binding level on purpose: a missing parameter
     * would otherwise fall into the catch-all 500 handler, while the service
     * answers blank input with a 400.
     */
    @GetMapping("/resolve")
    public ApiResponse<SessionResolveResponse> resolve(@RequestParam(required = false) String code) {
        CurrentUser currentUser = CurrentUserContext.required();
        return ApiResponse.success(new SessionResolveResponse(
                sessionService.resolveSessionCode(code, currentUser.tenantId(), currentUser.userId())));
    }
}
