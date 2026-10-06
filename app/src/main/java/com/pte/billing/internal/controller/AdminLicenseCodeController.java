package com.pte.billing.internal.controller;

import com.pte.billing.internal.dto.request.IssueLicenseCodeRequest;
import com.pte.billing.internal.dto.response.LicenseIssueReceipt;
import com.pte.billing.internal.service.LicenseCodeService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/license-codes")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AdminLicenseCodeController {
    private final LicenseCodeService service;

    public AdminLicenseCodeController(LicenseCodeService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ApiResponse<LicenseIssueReceipt>> issue(
            @RequestHeader(value = "Idempotency-Key", required = false) String key, @Valid @RequestBody IssueLicenseCodeRequest request) {
        LicenseIssueReceipt receipt = service.issue(request.planId(), request.codeExpiresAt(), IssueLicenseCodeRequest.parseIdempotencyKey(key),
                CurrentUserContext.required());
        return ResponseEntity.status(receipt.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.success(receipt));
    }
}
