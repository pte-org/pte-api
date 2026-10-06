package com.pte.billing.internal.controller;

import com.pte.billing.internal.dto.request.IssueLicenseCodeRequest;
import com.pte.billing.internal.dto.request.ConfirmLicenseRevokeRequest;
import com.pte.billing.internal.dto.request.LookupLicenseCodeRequest;
import com.pte.billing.internal.dto.response.AdminLicenseCodeSummary;
import com.pte.billing.internal.dto.response.LicenseCodeRevealResponse;
import com.pte.billing.internal.dto.response.LicenseIssueReceipt;
import com.pte.billing.internal.dto.response.LicenseRevokePreviewResponse;
import com.pte.billing.internal.dto.response.LicenseRevokeResponse;
import com.pte.billing.internal.service.LicenseCodeService;
import com.pte.shared.web.PagedResult;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResult<AdminLicenseCodeSummary>>> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID planId,
            @RequestParam(required = false) UUID tenantId) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(ApiResponse.success(service.listForAdmin(page, size, status, planId, tenantId,
                        CurrentUserContext.required())));
    }

    @GetMapping("/{publicId}")
    public ApiResponse<AdminLicenseCodeSummary> get(@PathVariable UUID publicId) {
        return ApiResponse.success(service.getForAdmin(publicId, CurrentUserContext.required()));
    }

    @PostMapping("/lookup")
    public ApiResponse<AdminLicenseCodeSummary> lookup(@Valid @RequestBody LookupLicenseCodeRequest request) {
        return ApiResponse.success(service.lookupForAdmin(request.code(), CurrentUserContext.required()));
    }

    @PostMapping("/{publicId}/reveal")
    public ResponseEntity<ApiResponse<LicenseCodeRevealResponse>> reveal(@PathVariable UUID publicId) {
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .header("Pragma", "no-cache")
                .body(ApiResponse.success(service.revealForAdmin(publicId, CurrentUserContext.required())));
    }

    @GetMapping("/{publicId}/revoke-preview")
    public ApiResponse<LicenseRevokePreviewResponse> revokePreview(@PathVariable UUID publicId) {
        return ApiResponse.success(service.previewRevoke(publicId, CurrentUserContext.required()));
    }

    @PostMapping("/{publicId}/revoke")
    public ApiResponse<LicenseRevokeResponse> revoke(@PathVariable UUID publicId,
            @Valid @RequestBody ConfirmLicenseRevokeRequest request) {
        return ApiResponse.success(service.revoke(publicId, request, CurrentUserContext.required()));
    }
}
