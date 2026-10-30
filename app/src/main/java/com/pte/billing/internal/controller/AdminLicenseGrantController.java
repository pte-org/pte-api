package com.pte.billing.internal.controller;

import com.pte.billing.internal.dto.request.license.GrantLicenseRequest;
import com.pte.billing.internal.dto.request.license.IssueLicenseCodeRequest;
import com.pte.billing.internal.dto.request.license.PreviewLicenseGrantRequest;
import com.pte.billing.internal.dto.response.LicenseGrantPreviewResponse;
import com.pte.billing.internal.dto.response.LicenseGrantResponse;
import com.pte.billing.internal.service.license.LicenseGrantService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform grant of a plan to one organization under a contract; activation happens in the same request. */
@RestController
@RequestMapping("/api/v1/admin/license-grants")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')")
public class AdminLicenseGrantController {

    private final LicenseGrantService service;

    public AdminLicenseGrantController(LicenseGrantService service) {
        this.service = service;
    }

    @PostMapping("/preview")
    public ApiResponse<LicenseGrantPreviewResponse> preview(@Valid @RequestBody PreviewLicenseGrantRequest request) {
        return ApiResponse.success(service.preview(request, CurrentUserContext.required()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<LicenseGrantResponse>> grant(
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody GrantLicenseRequest request) {
        LicenseGrantResponse response = service.grant(request, IssueLicenseCodeRequest.parseIdempotencyKey(key),
                CurrentUserContext.required());
        return ResponseEntity.status(response.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }
}
