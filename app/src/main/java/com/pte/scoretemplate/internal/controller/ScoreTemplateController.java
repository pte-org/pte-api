package com.pte.scoretemplate.internal.controller;

import com.pte.scoretemplate.dto.request.ReplaceScoreTemplateItemsRequest;
import com.pte.scoretemplate.dto.request.ImportScoreTemplateRequest;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.scoretemplate.internal.service.ScoreTemplateAdminService;
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

/**
 * PLATFORM_ADMIN only, on every endpoint here — {@code list}/{@code get}
 * can return DRAFT/RETIRED templates, which must never be visible to a
 * host (FR-03; see phase-01 red-team finding). Plan A gives no other role
 * any HTTP access to score templates: a host consuming the ACTIVE template
 * for exam generation is Plan B's concern, added as its own narrower
 * endpoint then — not by widening this controller's role list.
 */
@RestController
@RequestMapping("/api/v1/score-templates")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class ScoreTemplateController {

    private final ScoreTemplateAdminService adminService;

    public ScoreTemplateController(ScoreTemplateAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    public ApiResponse<List<ScoreTemplateResponse>> list() {
        return ApiResponse.success(adminService.listAll());
    }

    @GetMapping("/{publicId}")
    public ApiResponse<ScoreTemplateResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(adminService.getForEdit(publicId));
    }

    @PostMapping("/import")
    public ApiResponse<ScoreTemplateResponse> importTemplate(
            @Valid @RequestBody ImportScoreTemplateRequest request) {
        return ApiResponse.success(adminService.importAsDraft(request));
    }

    @PostMapping("/{publicId}/clone")
    public ApiResponse<ScoreTemplateResponse> clone(@PathVariable UUID publicId) {
        return ApiResponse.success(adminService.cloneToDraft(publicId));
    }

    @PutMapping("/{publicId}/items")
    public ApiResponse<ScoreTemplateResponse> replaceItems(@PathVariable UUID publicId,
                                                            @Valid @RequestBody ReplaceScoreTemplateItemsRequest request) {
        return ApiResponse.success(adminService.replaceItems(publicId, request));
    }

    @PostMapping("/{publicId}/activate")
    public ApiResponse<ScoreTemplateResponse> activate(@PathVariable UUID publicId) {
        return ApiResponse.success(adminService.activate(publicId));
    }
}
