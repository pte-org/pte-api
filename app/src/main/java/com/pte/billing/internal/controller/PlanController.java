package com.pte.billing.internal.controller;

import com.pte.billing.internal.dto.request.PlanRequest;
import com.pte.billing.internal.dto.request.PlanTransitionRequest;
import com.pte.billing.internal.dto.request.PlanUpdateRequest;
import com.pte.billing.internal.dto.response.PlanResponse;
import com.pte.billing.internal.service.PlanService;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.PlatformOperation;
import com.pte.shared.security.PlatformOperationPolicy;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

/** Platform catalog management plus the public active catalog. */
@RestController
@RequestMapping("/api/v1")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping("/plans")
    public ApiResponse<List<PlanResponse>> list(Authentication authentication) {
        CurrentUser caller = CurrentUserContext.current().orElse(null);
        boolean platformOperator = caller != null
                && PlatformOperationPolicy.can(caller, PlatformOperation.PLAN_READ_ALL);
        if (caller == null && authentication != null) {
            platformOperator = authentication.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_PLATFORM_ADMIN".equals(authority.getAuthority())
                            || "ROLE_PLATFORM_MANAGER".equals(authority.getAuthority()));
        }
        if (platformOperator) {
            return ApiResponse.success(caller == null ? planService.listForAdmin() : planService.listForAdmin(caller));
        }
        return ApiResponse.success(planService.listActive());
    }

    @PostMapping("/plans")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')")
    public ApiResponse<PlanResponse> create(@Valid @RequestBody PlanRequest request) {
        return ApiResponse.success(planService.create(request, CurrentUserContext.required()));
    }

    @GetMapping("/plans/{publicId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')")
    public ApiResponse<PlanResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(planService.get(publicId, CurrentUserContext.required()));
    }

    @DeleteMapping("/plans/{publicId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')")
    public ResponseEntity<Void> deleteDraft(@PathVariable UUID publicId) {
        planService.deleteDraft(publicId, CurrentUserContext.required());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/plans/{publicId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')")
    public ApiResponse<PlanResponse> update(@PathVariable UUID publicId,
            @Valid @RequestBody PlanUpdateRequest request) {
        return ApiResponse.success(planService.update(publicId, request, CurrentUserContext.required()));
    }

    @PostMapping("/plans/{publicId}/activation")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')")
    public ApiResponse<PlanResponse> activate(@PathVariable UUID publicId,
            @Valid @RequestBody PlanTransitionRequest request) {
        return ApiResponse.success(planService.activate(publicId, request, CurrentUserContext.required()));
    }

    @PostMapping("/plans/{publicId}/archive")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')")
    public ApiResponse<PlanResponse> archive(@PathVariable UUID publicId,
            @Valid @RequestBody PlanTransitionRequest request) {
        return ApiResponse.success(planService.archive(publicId, request, CurrentUserContext.required()));
    }
}
