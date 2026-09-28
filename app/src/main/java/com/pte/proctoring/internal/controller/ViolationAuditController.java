package com.pte.proctoring.internal.controller;

import com.pte.proctoring.internal.dto.response.ViolationEventResponse;
import com.pte.proctoring.internal.dto.response.SecurityAuditPageResponse;
import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.proctoring.internal.service.ViolationService;
import com.pte.proctoring.internal.service.SecurityAuditQueryService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Post-hoc audit review across every proctor who watched a given exam session — {@code sessionPublicId} is session's ExamSession id. */
@RestController
@RequestMapping("/api/v1/exam-sessions")
@PreAuthorize("hasAnyRole('PROCTOR','HOST_ADMIN')")
public class ViolationAuditController {

    private final ViolationService violationService;
    private final SecurityAuditQueryService securityAuditQueryService;

    public ViolationAuditController(ViolationService violationService,
                                    SecurityAuditQueryService securityAuditQueryService) {
        this.violationService = violationService;
        this.securityAuditQueryService = securityAuditQueryService;
    }

    @GetMapping("/{sessionPublicId}/violations")
    public ApiResponse<List<ViolationEventResponse>> listViolations(@PathVariable UUID sessionPublicId) {
        return ApiResponse.success(violationService.listForSession(sessionPublicId, currentUser()));
    }

    @GetMapping("/{sessionPublicId}/security-audit")
    public ApiResponse<SecurityAuditPageResponse> listSecurityAudit(
            @PathVariable UUID sessionPublicId,
            @RequestParam(defaultValue = "" + ProctorConstants.SECURITY_AUDIT_DEFAULT_LIMIT) int limit,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.success(securityAuditQueryService.listForSession(sessionPublicId, limit, cursor,
                currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
