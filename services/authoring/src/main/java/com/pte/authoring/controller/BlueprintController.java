package com.pte.authoring.controller;

import com.pte.authoring.dto.request.CreateBlueprintRequest;
import com.pte.authoring.dto.response.BlueprintResponse;
import com.pte.authoring.service.BlueprintService;
import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/blueprints")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_AUTHOR','HOST_ADMIN','HOST_AUTHOR')")
public class BlueprintController {

    private final BlueprintService blueprintService;

    public BlueprintController(BlueprintService blueprintService) {
        this.blueprintService = blueprintService;
    }

    @PostMapping
    public ApiResponse<BlueprintResponse> create(@Valid @RequestBody CreateBlueprintRequest request) {
        return ApiResponse.success(blueprintService.create(request, currentUser()));
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
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
