package com.pte.proctoring.internal.controller;

import com.pte.attempt.dto.response.MonitoringAttemptSummaryView;
import com.pte.proctoring.internal.dto.response.MonitoringSnapshotResponse;
import com.pte.proctoring.internal.service.MonitoringQueryService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for authoritative proctor monitoring snapshots and student attempt history.
 */
@RestController
@RequestMapping("/api/v1/exam-sessions")
@PreAuthorize("hasRole('PROCTOR')")
public class MonitoringController {

    private final MonitoringQueryService monitoringQueryService;

    public MonitoringController(MonitoringQueryService monitoringQueryService) {
        this.monitoringQueryService = monitoringQueryService;
    }

    @GetMapping("/{sessionPublicId}/monitoring")
    public ApiResponse<MonitoringSnapshotResponse> getSnapshot(
            @PathVariable UUID sessionPublicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(monitoringQueryService.getSnapshot(sessionPublicId, page, size, caller));
    }

    @GetMapping("/{sessionPublicId}/monitoring/students/{studentPublicId}/attempts")
    public ApiResponse<PagedResult<MonitoringAttemptSummaryView>> getStudentAttempts(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID studentPublicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(monitoringQueryService.getStudentAttemptHistory(
                sessionPublicId, studentPublicId, page, size, caller));
    }
}
