package com.pte.billing.internal.controller;

import com.pte.billing.internal.dto.request.RejectApplicationRequest;
import com.pte.billing.internal.dto.request.SubmitApplicationRequest;
import com.pte.billing.internal.dto.response.TenantApplicationResponse;
import com.pte.billing.internal.service.TenantApplicationService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
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

/**
 * {@code submit} is the one PUBLIC write endpoint in this whole codebase with
 * no JWT in front of it — see {@code SecurityConfig.PUBLIC_PATHS}, which
 * permits exactly the literal path {@code /applications} (this class maps no
 * other method there, so that doesn't accidentally open anything else).
 * The authenticated list/review endpoints are available to the explicit
 * platform operations roles; the public submit route remains unauthenticated.
 */
@RestController
@RequestMapping("/api/v1")
public class TenantApplicationController {

    private final TenantApplicationService applicationService;

    public TenantApplicationController(TenantApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/applications")
    public ApiResponse<Void> submit(@Valid @RequestBody SubmitApplicationRequest request) {
        applicationService.submit(request);
        return ApiResponse.success(null);
    }

    @GetMapping("/applications")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')")
    public ApiResponse<List<TenantApplicationResponse>> list() {
        return ApiResponse.success(applicationService.list(currentUser()));
    }

    @GetMapping("/applications/{publicId}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')")
    public ApiResponse<TenantApplicationResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(applicationService.get(publicId, currentUser()));
    }

    @PostMapping("/applications/{publicId}/approval")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')")
    public ApiResponse<Void> approve(@PathVariable UUID publicId) {
        applicationService.approve(publicId, currentUser());
        return ApiResponse.success(null);
    }

    @PostMapping("/applications/{publicId}/rejection")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')")
    public ApiResponse<TenantApplicationResponse> reject(@PathVariable UUID publicId,
            @Valid @RequestBody RejectApplicationRequest request) {
        return ApiResponse.success(applicationService.reject(publicId, request, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
