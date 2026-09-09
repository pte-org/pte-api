package com.pte.examdelivery.controller;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import com.pte.examdelivery.service.AttemptService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Presence signal for the parallel connectivity-monitoring feature
 * (client-side-exam-timer Phase 2, FR-04) — replaces {@code TimerController}'s
 * incidental role as a heartbeat carrier (deleted in Phase 5) with a purpose-built,
 * minimal endpoint that carries no timer/deadline/task data at all.
 */
@RestController
@RequestMapping("/attempts")
@PreAuthorize("hasRole('STUDENT')")
public class HeartbeatController {

    private final AttemptService attemptService;

    public HeartbeatController(AttemptService attemptService) {
        this.attemptService = attemptService;
    }

    @PostMapping("/{publicId}/heartbeat")
    public ApiResponse<Void> heartbeat(@PathVariable UUID publicId) {
        attemptService.recordHeartbeat(publicId, currentUser());
        return ApiResponse.success(null);
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
