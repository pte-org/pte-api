package com.pte.shared.audit.internal.controller;

import com.pte.shared.audit.dto.AuditLogResponse;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only tenant-scoped audit endpoint. */
@RestController
@RequestMapping("/api/v1/audit-logs")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<PagedResult<AuditLogResponse>> list(
            @RequestParam(required = false) String aggregateType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(auditLogService.list(currentUser(), aggregateType, page, size));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
