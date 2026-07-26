package com.pte.iam.controller;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import com.pte.iam.dto.request.CreateUserRequest;
import com.pte.iam.dto.response.UserResponse;
import com.pte.iam.service.UserService;
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
@RequestMapping("/users")
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

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
