package com.pte.scheduling.controller;

import com.pte.common.web.ApiResponse;
import com.pte.scheduling.dto.response.EntitlementResponse;
import com.pte.scheduling.dto.response.ProctorAssignmentCheckResponse;
import com.pte.scheduling.service.EntitlementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service only (ROLE_INTERNAL_SERVICE). Called by exam-delivery at
 * attempt-create, and by proctor at ProctorSession-open (phase-10).
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
