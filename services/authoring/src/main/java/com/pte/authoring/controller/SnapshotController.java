package com.pte.authoring.controller;

import com.pte.authoring.dto.response.SnapshotResponse;
import com.pte.authoring.service.SnapshotPublishService;
import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Publishing a blueprint to an immutable snapshot, and reading snapshots.
 * Snapshot read is what scheduling/exam-delivery pull to pin an attempt (the one
 * allowed create-time sync call).
 */
@RestController
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR','HOST_ADMIN','HOST_AUTHOR')")
public class SnapshotController {

    private final SnapshotPublishService snapshotPublishService;

    public SnapshotController(SnapshotPublishService snapshotPublishService) {
        this.snapshotPublishService = snapshotPublishService;
    }

    @PostMapping("/blueprints/{publicId}/publish")
    public ApiResponse<SnapshotResponse> publish(@PathVariable UUID publicId) {
        return ApiResponse.success(snapshotPublishService.publish(publicId, currentUser()));
    }

    @GetMapping("/snapshots/{publicId}")
    public ApiResponse<SnapshotResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(snapshotPublishService.get(publicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
