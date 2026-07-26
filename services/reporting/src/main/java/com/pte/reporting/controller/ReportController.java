package com.pte.reporting.controller;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import com.pte.reporting.dto.response.ReportResponse;
import com.pte.reporting.service.ReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Student sees a report only once published (and only their own); host sees
 * any time within their tenant (review-before-publish). Role guard here is
 * broad (any authenticated actor with one of these roles); the fine-grained
 * ownership/publish check lives in {@link ReportService}.
 */
@RestController
@RequestMapping("/reports")
@PreAuthorize("hasAnyRole('STUDENT','HOST_ADMIN','HOST_AUTHOR','PLATFORM_ADMIN','PLATFORM_AUTHOR')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/attempts/{attemptPublicId}")
    public ApiResponse<ReportResponse> getReport(@PathVariable UUID attemptPublicId) {
        return ApiResponse.success(reportService.getReport(attemptPublicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
