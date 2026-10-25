package com.pte.proctoring.internal.controller;

import com.pte.proctoring.dto.incident.CreateIncidentRequest;
import com.pte.proctoring.dto.incident.IncidentHistoryItemView;
import com.pte.proctoring.dto.incident.IncidentView;
import com.pte.proctoring.dto.incident.UpdateIncidentStatusRequest;
import com.pte.proctoring.internal.service.ExamIncidentService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exam-sessions")
@PreAuthorize("hasAnyRole('PROCTOR', 'HOST_ADMIN', 'PLATFORM_ADMIN', 'PLATFORM_MANAGER')")
public class ExamIncidentController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ExamIncidentService incidentService;

    public ExamIncidentController(ExamIncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping("/{sessionPublicId}/incidents")
    public ApiResponse<PagedResult<IncidentView>> listIncidents(
            @PathVariable UUID sessionPublicId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser caller = CurrentUserContext.required();
        int safePage = Math.max(0, page);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return ApiResponse.success(incidentService.listIncidents(sessionPublicId, status, safePage, safeSize, caller));
    }

    @PostMapping("/{sessionPublicId}/incidents")
    public ApiResponse<IncidentView> createIncident(
            @PathVariable UUID sessionPublicId,
            @Valid @RequestBody CreateIncidentRequest request) {
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(incidentService.createIncident(sessionPublicId, request, caller));
    }

    @GetMapping("/{sessionPublicId}/incidents/{incidentPublicId}")
    public ApiResponse<IncidentView> getIncident(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID incidentPublicId) {
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(incidentService.getIncident(sessionPublicId, incidentPublicId, caller));
    }

    @GetMapping("/{sessionPublicId}/incidents/{incidentPublicId}/history")
    public ApiResponse<List<IncidentHistoryItemView>> getIncidentHistory(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID incidentPublicId) {
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(incidentService.getIncidentHistory(sessionPublicId, incidentPublicId, caller));
    }

    @PatchMapping("/{sessionPublicId}/incidents/{incidentPublicId}/status")
    public ApiResponse<IncidentView> updateIncidentStatus(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID incidentPublicId,
            @Valid @RequestBody UpdateIncidentStatusRequest request) {
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(incidentService.updateIncidentStatus(sessionPublicId, incidentPublicId, request, caller));
    }
}
