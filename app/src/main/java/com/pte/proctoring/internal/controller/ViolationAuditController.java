package com.pte.proctoring.internal.controller;

import com.pte.proctoring.internal.dto.response.ViolationEventResponse;
import com.pte.proctoring.internal.service.ViolationService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Post-hoc audit review across every proctor who watched a given exam session — {@code sessionPublicId} is session's ExamSession id. */
@RestController
@RequestMapping("/exam-sessions")
@PreAuthorize("hasAnyRole('PROCTOR','HOST_ADMIN','HOST_AUTHOR')")
public class ViolationAuditController {

    private final ViolationService violationService;

    public ViolationAuditController(ViolationService violationService) {
        this.violationService = violationService;
    }

    @GetMapping("/{sessionPublicId}/violations")
    public ApiResponse<List<ViolationEventResponse>> listViolations(@PathVariable UUID sessionPublicId) {
        return ApiResponse.success(violationService.listForSession(sessionPublicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
