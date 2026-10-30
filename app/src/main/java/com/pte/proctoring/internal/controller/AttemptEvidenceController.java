package com.pte.proctoring.internal.controller;

import com.pte.proctoring.internal.dto.request.RecordSecurityViolationRequest;
import com.pte.proctoring.internal.dto.response.SecurityViolationReceipt;
import com.pte.proctoring.internal.service.IncidentEvidenceService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The student client reports lockdown evidence for its own attempt. */
@RestController
@RequestMapping("/api/v1/attempts")
@PreAuthorize("hasRole('STUDENT')")
public class AttemptEvidenceController {

    private final IncidentEvidenceService evidenceService;

    public AttemptEvidenceController(IncidentEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @PostMapping("/{publicId}/security-violations")
    public ApiResponse<SecurityViolationReceipt> recordSecurityViolation(
            @PathVariable UUID publicId,
            @Valid @RequestBody RecordSecurityViolationRequest request) {
        return ApiResponse.success(evidenceService.record(publicId, request, CurrentUserContext.required()));
    }
}
