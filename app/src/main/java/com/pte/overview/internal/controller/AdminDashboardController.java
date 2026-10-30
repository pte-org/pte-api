package com.pte.overview.internal.controller;

import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.overview.internal.dto.response.AdminDashboardResponse;
import com.pte.overview.internal.service.admin.AdminDashboardService;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.security.PlatformOperationPolicy;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform admin dashboard aggregate. The role annotation is only the first gate: the
 * controller itself rejects any caller that carries a tenant id (a HOST_ADMIN gets 403) or
 * lacks a valid platform scope, and the service then decides per section what the caller
 * sees (academic roles receive FORBIDDEN sections, not a 403). The legacy PLATFORM_AUTHOR
 * token is canonicalized to ACADEMIC_STAFF before any decision.
 */
@RestController
@RequestMapping(OverviewConstants.API_BASE)
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping(OverviewConstants.ADMIN_PATH)
    public ApiResponse<AdminDashboardResponse> admin(
            @RequestParam(name = OverviewConstants.PARAM_WINDOW_DAYS, required = false) String windowDays) {
        CurrentUser caller = CurrentUserContext.required();
        if (!PlatformOperationPolicy.isPlatformCaller(caller)) {
            throw new AccessDeniedException(SharedConstants.ACCESS_DENIED);
        }
        return ApiResponse.success(adminDashboardService.build(caller, windowDays));
    }
}
