package com.pte.practice.internal.controller;

import com.pte.identity.IdentityTokenResponse;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.dto.request.PracticeEmailRequest;
import com.pte.practice.internal.dto.request.PracticeVerifyRequest;
import com.pte.practice.internal.dto.response.PracticeChallengeResponse;
import com.pte.practice.internal.service.PracticeEmailAuthService;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public passwordless entry point for the isolated student practice web. */
@RestController
@RequestMapping(PracticeConstants.PRACTICE_AUTH_BASE_PATH)
@ConditionalOnProperty(name = PracticeConstants.PRACTICE_EMAIL_AUTH_ENABLED_PROPERTY, havingValue = "true")
public class PracticeAuthController {

    private final PracticeEmailAuthService authService;

    public PracticeAuthController(PracticeEmailAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/request")
    public ApiResponse<PracticeChallengeResponse> request(@Valid @RequestBody PracticeEmailRequest request) {
        return ApiResponse.success(authService.requestChallenge(request));
    }

    @PostMapping("/verify")
    public ApiResponse<IdentityTokenResponse> verify(@Valid @RequestBody PracticeVerifyRequest request) {
        return ApiResponse.success(authService.verifyChallenge(request));
    }
}
