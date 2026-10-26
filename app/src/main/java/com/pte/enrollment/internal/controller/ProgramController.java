package com.pte.enrollment.internal.controller;

import com.pte.enrollment.internal.dto.request.CreateProgramRequest;
import com.pte.enrollment.internal.dto.request.TrainingStatusRequest;
import com.pte.enrollment.internal.dto.request.UpdateProgramRequest;
import com.pte.enrollment.internal.dto.response.ProgramDashboardResponse;
import com.pte.enrollment.internal.dto.response.ProgramResponse;
import com.pte.enrollment.internal.service.ProgramService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Host self-service Program (Khá»‘i/KhÃ³a) CRUD, scoped to the caller's own tenant via {@code caller.tenantId()}. */
@RestController
@RequestMapping("/api/v1/organizations/{organizationPublicId}/programs")
@PreAuthorize("hasRole('HOST_ADMIN')")
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

    @PatchMapping("/{publicId}/status")
    public ApiResponse<ProgramResponse> changeStatus(@PathVariable UUID organizationPublicId, @PathVariable UUID publicId,
            @Valid @RequestBody TrainingStatusRequest request) {
        CurrentUser caller = currentUser();
        return ApiResponse.success(switch (request.action()) {
            case ACTIVATE -> programService.activate(organizationPublicId, publicId, caller);
            case DEACTIVATE -> programService.deactivate(organizationPublicId, publicId, caller);
            case SUSPEND -> programService.suspend(organizationPublicId, publicId, caller);
            case ARCHIVE -> programService.archive(organizationPublicId, publicId, caller);
        });
    }

    @GetMapping("/{publicId}/dashboard")
    public ApiResponse<ProgramDashboardResponse> dashboard(@PathVariable UUID organizationPublicId,
            @PathVariable UUID publicId) {
        return ApiResponse.success(programService.getDashboard(organizationPublicId, publicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
