package com.pte.session.internal.controller;

import com.pte.session.dto.response.EntitlementResponse;
import com.pte.session.dto.response.ProctorAssignmentCheckResponse;
import com.pte.session.internal.service.EntitlementService;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Compatibility adapter for external callers still on HTTP during dual-run.
 * Code inside {@code app} must call {@link com.pte.session.SessionService}
 * directly, never this controller.
 */
@RestController
@RequestMapping("/internal/sessions")
@PreAuthorize("hasRole('INTERNAL_SERVICE')")
public class InternalSessionController {

    private final EntitlementService entitlementService;

    public InternalSessionController(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @GetMapping("/{publicId}/entitlement")
    public ApiResponse<EntitlementResponse> checkEntitlement(@PathVariable UUID publicId,
                                                              @RequestParam UUID studentPublicId) {
        return ApiResponse.success(entitlementService.checkEntitlement(publicId, studentPublicId));
    }

    @GetMapping("/{publicId}/proctor-assignment")
    public ApiResponse<ProctorAssignmentCheckResponse> checkProctorAssignment(@PathVariable UUID publicId,
                                                                               @RequestParam UUID proctorPublicId) {
        return ApiResponse.success(entitlementService.checkProctorAssignment(publicId, proctorPublicId));
    }
}
