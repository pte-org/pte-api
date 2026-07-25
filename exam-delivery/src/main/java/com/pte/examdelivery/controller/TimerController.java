package com.pte.examdelivery.controller;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import com.pte.examdelivery.dto.response.TimerStateResponse;
import com.pte.examdelivery.service.AttemptService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Heartbeat poll target for the client's countdown UI (client timer is UX only). */
@RestController
@RequestMapping("/attempts")
@PreAuthorize("hasRole('STUDENT')")
public class TimerController {

    private final AttemptService attemptService;

    public TimerController(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    @GetMapping("/{publicId}/timer")
    public ApiResponse<TimerStateResponse> heartbeat(@PathVariable UUID publicId) {
        return ApiResponse.success(attemptService.getTimerState(publicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
