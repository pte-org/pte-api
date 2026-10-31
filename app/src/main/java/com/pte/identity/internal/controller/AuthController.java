package com.pte.identity.internal.controller;

import com.pte.identity.internal.dto.request.auth.LoginRequest;
import com.pte.identity.internal.dto.request.auth.LoginOrganizationOptionsRequest;
import com.pte.identity.internal.dto.request.auth.RefreshRequest;
import com.pte.identity.internal.dto.request.auth.ChangePasswordRequest;
import com.pte.identity.internal.dto.request.auth.PasswordResetConfirmRequest;
import com.pte.identity.internal.dto.request.auth.PasswordResetRequest;
import com.pte.identity.internal.service.PasswordResetDispatcher;
import com.pte.identity.internal.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import com.pte.identity.internal.dto.request.profile.UpdateOwnProfileRequest;
import com.pte.identity.internal.dto.response.TokenResponse;
import com.pte.identity.internal.dto.response.LoginOrganizationOptionResponse;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.service.AuthService;
import com.pte.identity.internal.service.UserService;
import com.pte.identity.internal.service.UserProfileService;
import com.pte.identity.internal.util.ClientAddress;
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
    private final PasswordResetService passwordResetService;
    private final PasswordResetDispatcher passwordResetDispatcher;

    public AuthController(AuthService authService, UserService userService, UserProfileService userProfileService,
            PasswordResetService passwordResetService, PasswordResetDispatcher passwordResetDispatcher) {
        this.authService = authService;
        this.userService = userService;
        this.userProfileService = userProfileService;
        this.passwordResetService = passwordResetService;
        this.passwordResetDispatcher = passwordResetDispatcher;
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

    /**
     * Answers at once and does the work afterwards, so neither the body nor the response time reveals whether the
     * address has an account.
     */
    @PostMapping("/password-reset/request")
    public ApiResponse<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest http) {
        passwordResetDispatcher.requestAsync(request.email(),
                ClientAddress.resolve(http.getHeader("X-Forwarded-For"), http.getRemoteAddr()));
        return ApiResponse.success(null);
    }

    @PostMapping("/password-reset/confirm")
    public ApiResponse<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirm(request.token(), request.newPassword());
        return ApiResponse.success(null);
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changeOwnPassword(request, CurrentUserContext.required());
        return ApiResponse.success(null);
    }
}
