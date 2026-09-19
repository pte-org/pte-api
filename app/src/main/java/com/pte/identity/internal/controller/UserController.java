package com.pte.identity.internal.controller;

import com.pte.identity.internal.dto.request.BulkCreateUsersRequest;
import com.pte.identity.internal.dto.request.CreateUserRequest;
import com.pte.identity.internal.dto.request.ResetPasswordRequest;
import com.pte.identity.internal.dto.response.BulkCreateUsersResponse;
import com.pte.identity.internal.dto.response.GeneratedCredentialsResponse;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.service.ExamStaffQueryService;
import com.pte.identity.internal.service.UserService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Tenant-user provisioning. The service applies the role hierarchy and tenant
 * scope so a Host can only touch lower roles in its own tenant and a platform
 * admin can only touch HOST_ADMIN accounts.
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOST_ADMIN')")
public class UserController {

    private final UserService userService;
    private final ExamStaffQueryService examStaffQueryService;

    public UserController(UserService userService, ExamStaffQueryService examStaffQueryService) {
        this.userService = userService;
        this.examStaffQueryService = examStaffQueryService;
    }

    @PostMapping
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success(userService.create(request, currentUser()));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('HOST_ADMIN')")
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

    @GetMapping("/exam-staff")
    public ApiResponse<PagedResult<UserResponse>> listExamStaff(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String direction) {
        return ApiResponse.success(examStaffQueryService.search(page, size, search, role, status, sort, direction,
                currentUser()));
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
    // role hierarchy are enforced in UserService.
    @PostMapping("/{publicId}/reset-password")
    public ApiResponse<UserResponse> resetPassword(@PathVariable UUID publicId,
                                                    @Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.success(userService.resetPassword(publicId, request, currentUser()));
    }

    @PostMapping("/{publicId}/credentials/send-email")
    public ApiResponse<GeneratedCredentialsResponse> sendCredentialsEmail(@PathVariable UUID publicId) {
        return ApiResponse.success(userService.sendGeneratedCredentials(publicId, currentUser()));
    }

    @PostMapping("/{publicId}/credentials/generate")
    public ApiResponse<GeneratedCredentialsResponse> generateStudentCredentials(@PathVariable UUID publicId) {
        return ApiResponse.success(userService.generateStudentCredentials(publicId, currentUser()));
    }

    // Separate from GET /users (which is caller-tenant-scoped). A platform admin
    // can use this to look up the tenant's HOST_ADMIN account only.
    @GetMapping("/by-tenant/{tenantId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ApiResponse<List<UserResponse>> listByTenant(@PathVariable UUID tenantId) {
        return ApiResponse.success(userService.listForTenant(tenantId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
