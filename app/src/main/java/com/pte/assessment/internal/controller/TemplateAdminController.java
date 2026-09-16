package com.pte.assessment.internal.controller;

import com.pte.assessment.internal.dto.request.TemplateRequest;
import com.pte.assessment.internal.dto.response.TemplateFeasibilityResponse;
import com.pte.assessment.internal.dto.response.TemplateResponse;
import com.pte.assessment.internal.service.TemplateService;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Platform-author/admin management of the platform-owned template catalog. */
@RestController
@RequestMapping("/api/admin/templates")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')")
public class TemplateAdminController {

    private final TemplateService templateService;

    public TemplateAdminController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @PostMapping
    public ApiResponse<TemplateResponse> create(@Valid @RequestBody TemplateRequest request) {
        return ApiResponse.success(templateService.create(request));
    }

    @GetMapping
    public ApiResponse<List<TemplateResponse>> list() {
        return ApiResponse.success(templateService.listForAdmin());
    }

    @GetMapping("/{publicId}")
    public ApiResponse<TemplateResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(templateService.get(publicId));
    }

    @PutMapping("/{publicId}")
    public ApiResponse<TemplateResponse> update(@PathVariable UUID publicId,
                                                 @Valid @RequestBody TemplateRequest request) {
        return ApiResponse.success(templateService.update(publicId, request));
    }

    @PostMapping("/{publicId}/activate")
    public ApiResponse<TemplateResponse> activate(@PathVariable UUID publicId) {
        return ApiResponse.success(templateService.activate(publicId));
    }

    @PostMapping("/{publicId}/archive")
    public ApiResponse<TemplateResponse> archive(@PathVariable UUID publicId) {
        return ApiResponse.success(templateService.archive(publicId));
    }

    @PostMapping("/{publicId}/clone")
    public ApiResponse<TemplateResponse> clone(@PathVariable UUID publicId) {
        return ApiResponse.success(templateService.clone(publicId));
    }

    @GetMapping("/{publicId}/feasibility")
    public ApiResponse<TemplateFeasibilityResponse> feasibility(@PathVariable UUID publicId) {
        return ApiResponse.success(templateService.feasibility(publicId));
    }
}
