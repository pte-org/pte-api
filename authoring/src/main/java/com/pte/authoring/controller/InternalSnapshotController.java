package com.pte.authoring.controller;

import com.pte.authoring.dto.response.SnapshotContentResponse;
import com.pte.authoring.service.SnapshotPublishService;
import com.pte.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service only (ROLE_INTERNAL_SERVICE, see {@code InternalSecurityConfig}).
 * Returns full snapshot content INCLUDING correct answers — never call this from
 * a human-facing flow.
 */
@RestController
@RequestMapping("/internal/snapshots")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
public class InternalSnapshotController {

    private final SnapshotPublishService snapshotPublishService;

    public InternalSnapshotController(SnapshotPublishService snapshotPublishService) {
        this.snapshotPublishService = snapshotPublishService;
    }

    @GetMapping("/{publicId}")
    public ApiResponse<SnapshotContentResponse> getContent(@PathVariable UUID publicId) {
        return ApiResponse.success(snapshotPublishService.getContent(publicId));
    }
}
