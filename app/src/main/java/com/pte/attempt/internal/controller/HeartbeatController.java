package com.pte.attempt.internal.controller;

import com.pte.attempt.internal.service.AttemptLifecycleService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Presence signal for the parallel connectivity-monitoring feature — carries no timer/deadline/task data at all. */
@RestController
@RequestMapping("/attempts")
@PreAuthorize("hasRole('STUDENT')")
public class HeartbeatController {

    private final AttemptLifecycleService attemptLifecycleService;

    public HeartbeatController(AttemptLifecycleService attemptLifecycleService) {
        this.attemptLifecycleService = attemptLifecycleService;
    }

    @PostMapping("/{publicId}/heartbeat")
    public ApiResponse<Void> heartbeat(@PathVariable UUID publicId) {
        attemptLifecycleService.recordHeartbeat(publicId, currentUser());
        return ApiResponse.success(null);
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
