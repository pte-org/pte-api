package com.pte.assessment.internal.controller;

import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.service.SnapshotPublishService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Publishing a blueprint to an immutable snapshot, and reading snapshots.
 * Snapshot read is what {@code session}/{@code attempt} reach through {@link
 * com.pte.assessment.AssessmentService} in-process — this endpoint is the
 * human-facing counterpart only.
 */
@RestController
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')")
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
        return CurrentUserContext.required();
    }
}
