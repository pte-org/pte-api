package com.pte.assessment.internal.controller;

import com.pte.assessment.internal.dto.response.BlueprintResponse;
import com.pte.assessment.internal.service.BlueprintService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/blueprints")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR')")
public class BlueprintController {

    private final BlueprintService blueprintService;

    public BlueprintController(BlueprintService blueprintService) {
        this.blueprintService = blueprintService;
    }

    @GetMapping("/{publicId}")
    public ApiResponse<BlueprintResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(blueprintService.get(publicId, currentUser()));
    }

    @GetMapping
    public ApiResponse<List<BlueprintResponse>> list() {
        return ApiResponse.success(blueprintService.list(currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
