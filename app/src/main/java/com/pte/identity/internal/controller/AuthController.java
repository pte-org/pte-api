package com.pte.identity.internal.controller;

import com.pte.identity.internal.dto.request.auth.LoginRequest;
import com.pte.identity.internal.dto.request.auth.LoginOrganizationOptionsRequest;
import com.pte.identity.internal.dto.request.auth.RefreshRequest;
import com.pte.identity.internal.dto.request.auth.ChangePasswordRequest;
import com.pte.identity.internal.dto.request.profile.UpdateOwnProfileRequest;
import com.pte.identity.internal.dto.response.TokenResponse;
import com.pte.identity.internal.dto.response.LoginOrganizationOptionResponse;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.service.AuthService;
import com.pte.identity.internal.service.UserService;
import com.pte.identity.internal.service.UserProfileService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final UserProfileService userProfileService;

    public AuthController(AuthService authService, UserService userService, UserProfileService userProfileService) {
        this.authService = authService;
        this.userService = userService;
        this.userProfileService = userProfileService;
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/login-options")
    public ApiResponse<List<LoginOrganizationOptionResponse>> loginOptions(
            @Valid @RequestBody LoginOrganizationOptionsRequest request) {
        return ApiResponse.success(authService.loginOrganizations(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me() {
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(userService.me(caller));
    }

    @PatchMapping("/me/profile")
    @PreAuthorize("hasRole('HOST_ADMIN')")
    public ApiResponse<UserResponse> updateMyProfile(@Valid @RequestBody UpdateOwnProfileRequest request) {
        return ApiResponse.success(userProfileService.updateOwnProfile(request, CurrentUserContext.required()));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changeOwnPassword(request, CurrentUserContext.required());
        return ApiResponse.success(null);
    }
}
