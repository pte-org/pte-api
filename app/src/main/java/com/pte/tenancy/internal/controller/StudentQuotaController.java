package com.pte.tenancy.internal.controller;

import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.tenancy.StudentQuota;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.internal.dto.request.StudentImportPreviewRequest;
import com.pte.tenancy.internal.dto.response.StudentQuotaResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant-facing student-capacity status and import preview endpoints.
 *
 * <p>No class-level {@code @RequestMapping}: the two methods below own
 * unrelated path roots ({@code /tenant/...} and {@code /students/...}), so
 * there is nothing common to factor out. The edge (Caddy
 * {@code handle_path /api/*}) strips the {@code /api} prefix before proxying
 * — see {@code deploy/api-routes.caddy}.
 */
@RestController
@PreAuthorize("hasRole('HOST_ADMIN')")
public class StudentQuotaController {

    private final TenancyService tenancyService;

    public StudentQuotaController(TenancyService tenancyService) {
        this.tenancyService = tenancyService;
    }

    @GetMapping("/tenant/quota")
    public ApiResponse<StudentQuotaResponse> quota() {
        StudentQuota quota = tenancyService.getStudentQuota(currentUser().tenantId());
        return ApiResponse.success(StudentQuotaResponse.from(quota, 0L));
    }

    @PostMapping("/students/import/preview")
    public ApiResponse<StudentQuotaResponse> preview(@Valid @RequestBody StudentImportPreviewRequest request) {
        StudentQuota quota = tenancyService.getStudentQuota(currentUser().tenantId());
        return ApiResponse.success(StudentQuotaResponse.from(quota, request.adding()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
