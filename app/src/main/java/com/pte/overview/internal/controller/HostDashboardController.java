package com.pte.overview.internal.controller;

import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.overview.internal.dto.response.HostDashboardResponse;
import com.pte.overview.internal.service.HostDashboardService;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant host dashboard aggregate. HOST_ADMIN only; the tenant is taken from the
 * token, so there is deliberately no tenant parameter.
 */
@RestController
@RequestMapping(OverviewConstants.API_BASE)
@PreAuthorize("hasRole('HOST_ADMIN')")
public class HostDashboardController {

    private final HostDashboardService hostDashboardService;

    public HostDashboardController(HostDashboardService hostDashboardService) {
        this.hostDashboardService = hostDashboardService;
    }

    @GetMapping(OverviewConstants.HOST_PATH)
    public ApiResponse<HostDashboardResponse> host(
            @RequestParam(name = OverviewConstants.PARAM_WINDOW_DAYS, required = false) String windowDays) {
        CurrentUser caller = CurrentUserContext.required();
        if (caller.tenantId() == null) {
            throw new AccessDeniedException(SharedConstants.ACCESS_DENIED);
        }
        return ApiResponse.success(hostDashboardService.build(caller.tenantId(), windowDays));
    }
}
