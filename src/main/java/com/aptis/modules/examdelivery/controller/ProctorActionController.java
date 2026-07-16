package com.aptis.modules.examdelivery.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examdelivery.dto.ExtendTimeRequest;
import com.aptis.modules.examdelivery.dto.ExtendTimeResponse;
import com.aptis.modules.examdelivery.dto.FlagViolationRequest;
import com.aptis.modules.examdelivery.dto.FlagViolationResponse;
import com.aptis.modules.examdelivery.dto.ProctorActionResponse;
import com.aptis.modules.examdelivery.service.ProctorActionService;
import com.aptis.modules.proctor.constant.ProctoringApiConstants;

import jakarta.validation.Valid;

/**
 * The 3 per-attempt privileged proctor actions. Broadcast-announcement (the 4th, one-to-many
 * action) lives in the `proctor` module instead — it doesn't mutate ExamAttempt.
 */
@RestController
@RequestMapping(ProctoringApiConstants.PROCTOR_ATTEMPTS)
public class ProctorActionController {

    private final ProctorActionService proctorActionService;

    public ProctorActionController(ProctorActionService proctorActionService) {
        this.proctorActionService = proctorActionService;
    }

    @PreAuthorize(ProctoringApiConstants.AUTHORITY_PROCTOR)
    @PostMapping(ProctoringApiConstants.PATH_FORCE_SUBMIT)
    public ResponseEntity<ProctorActionResponse> forceSubmit(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal JwtPrincipal principal) {

        return ResponseEntity.ok(proctorActionService.forceSubmit(attemptId, principal));
    }

    @PreAuthorize(ProctoringApiConstants.AUTHORITY_PROCTOR)
    @PostMapping(ProctoringApiConstants.PATH_EXTEND_TIME)
    public ResponseEntity<ExtendTimeResponse> extendTime(
            @PathVariable Long attemptId,
            @Valid @RequestBody ExtendTimeRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {

        return ResponseEntity.ok(proctorActionService.extendTime(attemptId, request, principal));
    }

    @PreAuthorize(ProctoringApiConstants.AUTHORITY_PROCTOR)
    @PostMapping(ProctoringApiConstants.PATH_FLAG_VIOLATION)
    public ResponseEntity<FlagViolationResponse> flagViolation(
            @PathVariable Long attemptId,
            @Valid @RequestBody FlagViolationRequest request,
            @AuthenticationPrincipal JwtPrincipal principal) {

        return ResponseEntity.ok(proctorActionService.flagViolation(attemptId, request.reason(), principal));
    }
}
