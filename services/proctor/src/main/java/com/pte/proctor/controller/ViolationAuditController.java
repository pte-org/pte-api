package com.pte.proctor.controller;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import com.pte.proctor.dto.response.ViolationEventResponse;
import com.pte.proctor.service.ViolationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Post-hoc audit review across every proctor who watched a given exam session — {@code sessionPublicId} is scheduling's ExamSession id. */
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
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
