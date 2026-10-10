package com.pte.identity.internal.controller;

import com.pte.identity.internal.dto.request.PlatformUserCreateRequest;
import com.pte.identity.internal.dto.request.PlatformUserRoleUpdateRequest;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.service.PlatformUserService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Dedicated platform-user API; it never reuses tenant-user URL scope. */
@RestController
@RequestMapping("/api/v1/platform-users")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlatformUserController {

    private final PlatformUserService platformUserService;

    public PlatformUserController(PlatformUserService platformUserService) {
        this.platformUserService = platformUserService;
    }

    @PostMapping
    public ApiResponse<UserResponse> create(@Valid @RequestBody PlatformUserCreateRequest request) {
        return ApiResponse.success(platformUserService.create(request, currentUser()));
    }

    @GetMapping
    public ApiResponse<PagedResult<UserResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {
        return ApiResponse.success(platformUserService.list(page, size, search, role, status, currentUser()));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<UserResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(platformUserService.get(publicId, currentUser()));
    }

    @PatchMapping("/{publicId}/roles")
    public ApiResponse<UserResponse> updateRoles(@PathVariable UUID publicId,
            @Valid @RequestBody PlatformUserRoleUpdateRequest request) {
        return ApiResponse.success(platformUserService.updateRoles(publicId, request, currentUser()));
    }

    @PostMapping("/{publicId}/suspend")
    public ApiResponse<UserResponse> suspend(@PathVariable UUID publicId) {
        return ApiResponse.success(platformUserService.suspend(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/reactivate")
    public ApiResponse<UserResponse> reactivate(@PathVariable UUID publicId) {
        return ApiResponse.success(platformUserService.reactivate(publicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
