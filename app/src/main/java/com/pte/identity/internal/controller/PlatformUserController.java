package com.pte.identity.internal.controller;

import com.pte.identity.internal.dto.request.user.PlatformUserCreateRequest;
import com.pte.identity.internal.dto.request.user.PlatformUserRoleUpdateRequest;
import com.pte.identity.internal.dto.request.profile.UpdatePlatformUserProfileRequest;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.service.PlatformUserLifecycleService;
import com.pte.identity.internal.service.PlatformUserService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import com.pte.identity.internal.dto.request.user.UserStatusRequest;
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
    private final PlatformUserLifecycleService platformUserLifecycleService;

    public PlatformUserController(PlatformUserService platformUserService,
            PlatformUserLifecycleService platformUserLifecycleService) {
        this.platformUserService = platformUserService;
        this.platformUserLifecycleService = platformUserLifecycleService;
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
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean includeArchived) {
        return ApiResponse.success(
                platformUserService.list(page, size, search, role, status, includeArchived, currentUser()));
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

    @PatchMapping("/{publicId}")
    public ApiResponse<UserResponse> updateProfile(@PathVariable UUID publicId,
            @Valid @RequestBody UpdatePlatformUserProfileRequest request) {
        return ApiResponse.success(platformUserLifecycleService.updateProfile(publicId, request, currentUser()));
    }

    @PostMapping("/{publicId}/reset-password")
    public ApiResponse<UserResponse> resetPassword(@PathVariable UUID publicId) {
        return ApiResponse.success(platformUserLifecycleService.resetPassword(publicId, currentUser()));
    }

    @PatchMapping("/{publicId}/status")
    public ApiResponse<UserResponse> changeStatus(@PathVariable UUID publicId,
            @Valid @RequestBody UserStatusRequest request) {
        CurrentUser caller = currentUser();
        return ApiResponse.success(switch (request.action()) {
            case SUSPEND -> platformUserService.suspend(publicId, caller);
            case REACTIVATE -> platformUserService.reactivate(publicId, caller);
            case ARCHIVE -> platformUserLifecycleService.archive(publicId, caller);
            case RESTORE -> platformUserLifecycleService.restore(publicId, caller);
        });
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
