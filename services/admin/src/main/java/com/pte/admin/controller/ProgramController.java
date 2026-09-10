package com.pte.admin.controller;

import com.pte.admin.dto.request.CreateProgramRequest;
import com.pte.admin.dto.request.UpdateProgramRequest;
import com.pte.admin.dto.response.ProgramDashboardResponse;
import com.pte.admin.dto.response.ProgramResponse;
import com.pte.admin.service.ProgramService;
import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
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

/** Host self-service Program (Khối/Khóa) CRUD, scoped to the caller's own tenant via {@code caller.tenantId()}. */
@RestController
@RequestMapping("/organizations/{organizationPublicId}/programs")
@PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR')")
public class ProgramController {

    private final ProgramService programService;

    public ProgramController(ProgramService programService) {
        this.programService = programService;
    }

    @PostMapping
    public ApiResponse<ProgramResponse> create(@PathVariable UUID organizationPublicId,
            @Valid @RequestBody CreateProgramRequest request) {
        return ApiResponse.success(programService.create(organizationPublicId, request, currentUser()));
    }

    @GetMapping
    public ApiResponse<List<ProgramResponse>> list(@PathVariable UUID organizationPublicId) {
        return ApiResponse.success(programService.list(organizationPublicId, currentUser()));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<ProgramResponse> get(@PathVariable UUID organizationPublicId, @PathVariable UUID publicId) {
        return ApiResponse.success(programService.get(organizationPublicId, publicId, currentUser()));
    }

    @PutMapping("/{publicId}")
    public ApiResponse<ProgramResponse> update(@PathVariable UUID organizationPublicId, @PathVariable UUID publicId,
            @Valid @RequestBody UpdateProgramRequest request) {
        return ApiResponse.success(programService.update(organizationPublicId, publicId, request, currentUser()));
    }

    @PostMapping("/{publicId}/activate")
    public ApiResponse<ProgramResponse> activate(@PathVariable UUID organizationPublicId,
            @PathVariable UUID publicId) {
        return ApiResponse.success(programService.activate(organizationPublicId, publicId, currentUser()));
    }

    @PostMapping("/{publicId}/deactivate")
    public ApiResponse<ProgramResponse> deactivate(@PathVariable UUID organizationPublicId,
            @PathVariable UUID publicId) {
        return ApiResponse.success(programService.deactivate(organizationPublicId, publicId, currentUser()));
    }

    @PostMapping("/{publicId}/suspend")
    public ApiResponse<ProgramResponse> suspend(@PathVariable UUID organizationPublicId,
            @PathVariable UUID publicId) {
        return ApiResponse.success(programService.suspend(organizationPublicId, publicId, currentUser()));
    }

    @PostMapping("/{publicId}/archive")
    public ApiResponse<ProgramResponse> archive(@PathVariable UUID organizationPublicId,
            @PathVariable UUID publicId) {
        return ApiResponse.success(programService.archive(organizationPublicId, publicId, currentUser()));
    }

    @GetMapping("/{publicId}/dashboard")
    public ApiResponse<ProgramDashboardResponse> dashboard(@PathVariable UUID organizationPublicId,
            @PathVariable UUID publicId) {
        return ApiResponse.success(programService.getDashboard(organizationPublicId, publicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
