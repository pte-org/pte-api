package com.pte.identity.internal.controller;

import com.pte.identity.internal.dto.request.LoginRequest;
import com.pte.identity.internal.dto.request.LoginOrganizationOptionsRequest;
import com.pte.identity.internal.dto.request.RefreshRequest;
import com.pte.identity.internal.dto.request.ChangePasswordRequest;
import com.pte.identity.internal.dto.response.TokenResponse;
import com.pte.identity.internal.dto.response.LoginOrganizationOptionResponse;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.service.AuthService;
import com.pte.identity.internal.service.UserService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
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

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
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
