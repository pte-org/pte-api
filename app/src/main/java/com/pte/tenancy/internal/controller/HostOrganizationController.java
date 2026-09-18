package com.pte.tenancy.internal.controller;

import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.tenancy.internal.dto.response.OrganizationResponse;
import com.pte.tenancy.internal.service.OrganizationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Host self-service read-only view of its single Organization. The Organization
 * is provisioned automatically with the Host and is not created from this UI.
 */
@RestController
@RequestMapping("/api/v1/organizations")
@PreAuthorize("hasRole('HOST_ADMIN')")
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
        return CurrentUserContext.required();
    }
}
