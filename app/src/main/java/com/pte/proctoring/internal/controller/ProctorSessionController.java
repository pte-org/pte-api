package com.pte.proctoring.internal.controller;

import com.pte.proctoring.internal.service.ProctorSessionService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** REST fallback for lifecycle actions that don't need a live WS round-trip. Opening a session is STOMP-only (see {@link ProctorStompController}). */
@RestController
@RequestMapping("/proctor-sessions")
@PreAuthorize("hasRole('PROCTOR')")
public class ProctorSessionController {

    private final ProctorSessionService proctorSessionService;

    public ProctorSessionController(ProctorSessionService proctorSessionService) {
        this.proctorSessionService = proctorSessionService;
    }

    @PostMapping("/{proctorSessionPublicId}/close")
    public ApiResponse<Void> close(@PathVariable UUID proctorSessionPublicId) {
        proctorSessionService.close(proctorSessionPublicId, currentUser());
        return ApiResponse.success(null);
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
