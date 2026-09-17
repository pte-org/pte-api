package com.pte.assessment.internal.controller;

import com.pte.assessment.internal.dto.response.TemplateResponse;
import com.pte.assessment.internal.service.TemplateService;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Tenant-facing active template catalog; no write or draft access is exposed. */
@RestController
@RequestMapping("/templates")
@PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR')")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public ApiResponse<List<TemplateResponse>> listActive() {
        return ApiResponse.success(templateService.listActive());
    }
}
