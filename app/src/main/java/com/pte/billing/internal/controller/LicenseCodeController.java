package com.pte.billing.internal.controller;

import com.pte.billing.internal.dto.request.IssueLicenseCodeRequest;
import com.pte.billing.internal.dto.request.RevokeLicenseCodeRequest;
import com.pte.billing.internal.dto.response.LicenseCodeResponse;
import com.pte.billing.internal.service.LicenseCodeService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Platform-admin issue/list/revoke operations for individual activation codes. */
@RestController
@RequestMapping("/admin/license-codes")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class LicenseCodeController {

    private final LicenseCodeService licenseCodeService;

    public LicenseCodeController(LicenseCodeService licenseCodeService) {
        this.licenseCodeService = licenseCodeService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LicenseCodeResponse>> issue(
            @Valid @RequestBody IssueLicenseCodeRequest request) {
        LicenseCodeResponse response = licenseCodeService.issue(request.planId(), request.codeExpiresAt(), currentUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ApiResponse<List<LicenseCodeResponse>> list() {
        return ApiResponse.success(licenseCodeService.list(currentUser()));
    }

    @PostMapping("/{code}/revoke")
    public ApiResponse<LicenseCodeResponse> revoke(@PathVariable String code,
            @Valid @RequestBody RevokeLicenseCodeRequest request) {
        return ApiResponse.success(licenseCodeService.revoke(code, request.reason(), currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
