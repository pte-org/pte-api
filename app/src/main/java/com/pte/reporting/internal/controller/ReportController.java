package com.pte.reporting.internal.controller;

import com.pte.reporting.internal.dto.response.ReportResponse;
import com.pte.reporting.internal.constant.ReportingConstants;
import com.pte.reporting.internal.service.ReportService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Host sees a report any time within their tenant (review-before-publish). Students have no report access at
 * all: they receive their scores only by email when the host publishes. Role guard here is broad (any
 * authenticated actor with one of these roles); the fine-grained tenant check lives in {@link ReportService}.
 */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('HOST_ADMIN','PLATFORM_ADMIN')")
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
                .orElseThrow(() -> new IllegalStateException(ReportingConstants.AUTHENTICATED_PRINCIPAL_REQUIRED));
    }
}
