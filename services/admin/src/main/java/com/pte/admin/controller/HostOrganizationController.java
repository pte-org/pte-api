package com.pte.admin.controller;

import com.pte.admin.dto.response.OrganizationResponse;
import com.pte.admin.service.OrganizationService;
import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Host self-service read-only view of its own Organizations (branches) — a
 * prerequisite for picking which Organization a new Program belongs to.
 * Creation/suspend/reactivate stay platform-admin-only via
 * {@link OrganizationController}; a Host cannot create its own branches.
 */
@RestController
@RequestMapping("/organizations")
@PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR')")
public class HostOrganizationController {

    private final OrganizationService organizationService;

    public HostOrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public ApiResponse<List<OrganizationResponse>> list() {
        return ApiResponse.success(organizationService.listForCaller(currentUser()));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<OrganizationResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(organizationService.getForCaller(publicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
