package com.aptis.modules.iam.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptis.common.response.ApiResponse;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.dto.request.auth.ChangePasswordRequest;
import com.aptis.modules.iam.dto.request.auth.LoginRequest;
import com.aptis.modules.iam.dto.request.auth.LogoutRequest;
import com.aptis.modules.iam.dto.request.auth.RefreshTokenRequest;
import com.aptis.modules.iam.dto.response.auth.AuthResponse;
import com.aptis.modules.iam.dto.response.auth.UserProfileResponse;
import com.aptis.modules.iam.interfaces.AuthOperations;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(IamApiConstants.AUTH_BASE)
public class AuthController {

    private final AuthOperations authService;

    public AuthController(AuthOperations authService) {
        this.authService = authService;
    }

    @PostMapping(IamApiConstants.PATH_LOGIN)
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.credential(), request.password());
    }

    @PostMapping(IamApiConstants.PATH_REFRESH)
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping(IamApiConstants.PATH_LOGOUT)
    public ApiResponse<Void> logout(
            @Valid @RequestBody LogoutRequest request,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {
        authService.logout(request.refreshToken(), principal);
        return ApiResponse.success(null, servletRequest.getRequestURI());
    }

    @GetMapping(IamApiConstants.PATH_ME)
    public UserProfileResponse getProfile(@AuthenticationPrincipal JwtPrincipal principal) {
        return authService.getProfile(principal);
    }

    @PreAuthorize(IamApiConstants.AUTHORITY_HOST)
    @PostMapping(IamApiConstants.PATH_CHANGE_PASSWORD)
    public ApiResponse<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {
        authService.changePassword(
                principal.userId(),
                request.currentPassword(),
                request.newPassword());
        return ApiResponse.success(null, servletRequest.getRequestURI());
    }
}
