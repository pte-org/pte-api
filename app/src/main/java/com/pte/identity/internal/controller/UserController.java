package com.pte.identity.internal.controller;

import com.pte.identity.internal.dto.request.BulkCreateUsersRequest;
import com.pte.identity.internal.dto.request.CreateUserRequest;
import com.pte.identity.internal.dto.request.ResetPasswordRequest;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.service.UserService;
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
 * Host/proctor/student provisioning. Guarded by role; the service further enforces
 * tenant scope so a host can only ever touch its own tenant's users.
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOST_ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success(userService.create(request, currentUser()));
    }

    @PostMapping("/bulk")
    public ApiResponse<BulkCreateUsersResponse> createBulk(@Valid @RequestBody BulkCreateUsersRequest request) {
        return ApiResponse.success(userService.createBulk(request, currentUser()));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<UserResponse> get(@PathVariable UUID publicId) {
        return ApiResponse.success(userService.get(publicId, currentUser()));
    }

    @GetMapping
    public ApiResponse<List<UserResponse>> list() {
        return ApiResponse.success(userService.listByTenant(currentUser()));
    }

    @PostMapping("/{publicId}/suspend")
    public ApiResponse<UserResponse> suspend(@PathVariable UUID publicId) {
        return ApiResponse.success(userService.suspend(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/reactivate")
    public ApiResponse<UserResponse> reactivate(@PathVariable UUID publicId) {
        return ApiResponse.success(userService.reactivate(publicId, currentUser()));
    }

    // No method-level @PreAuthorize override needed: tenant scope and the
    // STUDENT/PROCTOR-only role restriction are enforced in UserService#resetPassword.
    @PostMapping("/{publicId}/reset-password")
    public ApiResponse<UserResponse> resetPassword(@PathVariable UUID publicId,
                                                    @Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.success(userService.resetPassword(publicId, request, currentUser()));
    }

    // Separate from GET /users (which is caller-tenant-scoped) so a platform admin
    // can look up an arbitrary tenant's users without overloading that endpoint's
    // existing semantics.
    @GetMapping("/by-tenant/{tenantId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<List<UserResponse>> listByTenant(@PathVariable UUID tenantId) {
        return ApiResponse.success(userService.listForTenant(tenantId));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
