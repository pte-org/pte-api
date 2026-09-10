package com.pte.admin.controller;

import com.pte.admin.dto.response.AuditLogResponse;
import com.pte.admin.service.AuditLogService;
import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only audit trail for the caller's own tenant — {@code HOST_ADMIN} only, never path-scoped (always {@code caller.tenantId()}). */
@RestController
@RequestMapping("/audit-logs")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<List<AuditLogResponse>> list(@RequestParam(required = false) String aggregateType) {
        return ApiResponse.success(auditLogService.list(currentUser(), aggregateType));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
