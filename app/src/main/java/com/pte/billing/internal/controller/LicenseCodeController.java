package com.pte.billing.internal.controller;

import com.pte.billing.internal.dto.request.IssueLicenseCodeRequest;
import com.pte.billing.internal.dto.response.LicenseCodeResponse;
import com.pte.billing.internal.dto.response.LicenseIssueReceipt;
import org.springframework.web.bind.annotation.RequestHeader;
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
@RequestMapping("/api/v1/license-codes")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class LicenseCodeController {

    private final LicenseCodeService licenseCodeService;

    public LicenseCodeController(LicenseCodeService licenseCodeService) {
        this.licenseCodeService = licenseCodeService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LicenseIssueReceipt>> issue(
            @RequestHeader(value = "Idempotency-Key", required = false) String key, @Valid @RequestBody IssueLicenseCodeRequest request) {
        LicenseIssueReceipt response = licenseCodeService.issue(request.planId(), request.codeExpiresAt(),
                IssueLicenseCodeRequest.parseIdempotencyKey(key), currentUser());
        return ResponseEntity.status(response.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LicenseCodeResponse>>> list() {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(ApiResponse.success(licenseCodeService.list(currentUser())));
    }

    @PostMapping("/{ignored}/revoke")
    public ApiResponse<Void> revokeLegacy(@PathVariable String ignored) {
        licenseCodeService.rejectLegacyRevoke(currentUser());
        return ApiResponse.success(null);
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
