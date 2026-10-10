package com.pte.enrollment.internal.controller;

import com.pte.enrollment.internal.dto.response.StudentAttemptHistoryResponse;
import com.pte.enrollment.internal.dto.response.StudentDetailResponse;
import com.pte.enrollment.internal.dto.response.StudentPerformanceResponse;
import com.pte.enrollment.internal.service.StudentWorkspaceQueryService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Host-only student account, performance, and activity read APIs. */
@RestController
@RequestMapping("/api/v1/students")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class StudentWorkspaceController {

    private final StudentWorkspaceQueryService workspaceQueryService;

    public StudentWorkspaceController(StudentWorkspaceQueryService workspaceQueryService) {
        this.workspaceQueryService = workspaceQueryService;
    }

    @GetMapping("/{publicId}")
    public ApiResponse<StudentDetailResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(workspaceQueryService.getDetail(publicId, currentUser()));
    }

    @GetMapping("/{publicId}/attempts")
    public ApiResponse<PagedResult<StudentAttemptHistoryResponse>> history(
            @PathVariable UUID publicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(workspaceQueryService.getHistory(publicId, page, size, from, to, status,
                currentUser()));
    }

    @GetMapping("/{publicId}/performance")
    public ApiResponse<StudentPerformanceResponse> performance(
            @PathVariable UUID publicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(workspaceQueryService.getPerformance(publicId, from, to, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
