package com.pte.shared.audit.internal.controller;

import com.pte.shared.audit.AuditLogService;
import com.pte.shared.audit.dto.AuditLogResponse;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.security.PlatformOperationPolicy;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Read-only platform-scoped audit endpoint for vendor operations. */
@RestController
@RequestMapping("/api/v1/platform/audit-logs")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')")
public class PlatformAuditLogController {

    private final AuditLogService auditLogService;

    public PlatformAuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ApiResponse<PagedResult<AuditLogResponse>> list(
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        CurrentUser caller = CurrentUserContext.required();
        if (!PlatformOperationPolicy.isPlatformCaller(caller)) {
            throw new AccessDeniedException(SharedConstants.ACCESS_DENIED);
        }
        return ApiResponse.success(search == null || search.isBlank()
                ? auditLogService.listPlatform(caller, aggregateType, null, createdFrom, createdTo, page, size)
                : auditLogService.listPlatform(caller, aggregateType, search, createdFrom, createdTo, page, size));
    }
}
