package com.pte.shared.audit.internal.controller;

import com.pte.shared.audit.dto.AuditLogResponse;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Read-only tenant-scoped audit endpoint. */
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
        return CurrentUserContext.required();
    }
}
