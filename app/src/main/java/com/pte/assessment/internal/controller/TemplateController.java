package com.pte.assessment.internal.controller;

import com.pte.assessment.internal.dto.response.TemplateResponse;
import com.pte.assessment.internal.service.TemplateService;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Tenant-facing active template catalog; no write or draft access is exposed. */
@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR','PLATFORM_ADMIN','PLATFORM_AUTHOR')")
    public ApiResponse<List<TemplateResponse>> list(Authentication authentication) {
        boolean platformUser = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_PLATFORM_ADMIN")
                        || authority.getAuthority().equals("ROLE_PLATFORM_AUTHOR"));
        return ApiResponse.success(platformUser
                ? templateService.listForAdmin()
                : templateService.listActive());
    }
}
