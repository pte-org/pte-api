package com.pte.assessment.internal.controller;

import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.service.SnapshotPublishService;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Compatibility adapter for external callers still on HTTP during dual-run
 * (a service still in {@code services/} until Phase 11 cutover). Code inside
 * {@code app} must call {@link com.pte.assessment.AssessmentService} directly,
 * never this controller. {@link #getContent} returns full snapshot content
 * INCLUDING correct answers — never call that from a human-facing flow.
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

    @GetMapping("/{publicId}/summary")
    public ApiResponse<SnapshotResponse> getSummary(@PathVariable UUID publicId) {
        return ApiResponse.success(snapshotPublishService.getSummary(publicId));
    }
}
