package com.pte.shared.audit.internal.controller;

import com.pte.shared.audit.PlatformAuditFilter;
import com.pte.shared.audit.PlatformAuditLogQuery;
import com.pte.shared.audit.dto.AuditLogDetailResponse;
import com.pte.shared.audit.dto.AuditLogResponse;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.security.PlatformOperationPolicy;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** Read-only platform-scoped audit endpoint for vendor operations. */
@RestController
@RequestMapping("/api/v1/platform/audit-logs")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')")
public class PlatformAuditLogController {

    private final PlatformAuditLogQuery platformAuditLogQuery;

    public PlatformAuditLogController(PlatformAuditLogQuery platformAuditLogQuery) {
        this.platformAuditLogQuery = platformAuditLogQuery;
    }

    @GetMapping
    public ApiResponse<PagedResult<AuditLogResponse>> list(
            @RequestParam(required = false) String aggregateType,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requirePlatformCaller();
        PlatformAuditFilter filter = new PlatformAuditFilter(aggregateType, aggregateId, actorId, search,
                createdFrom, createdTo);
        return ApiResponse.success(platformAuditLogQuery.list(filter, page, size));
    }

    /** One entry with its before/after snapshots; these can include answer keys, so they stay platform-only. */
    @GetMapping("/{publicId}")
    public ApiResponse<AuditLogDetailResponse> detail(@PathVariable UUID publicId) {
        requirePlatformCaller();
        return ApiResponse.success(platformAuditLogQuery.detail(publicId));
    }

    private static void requirePlatformCaller() {
        if (!PlatformOperationPolicy.isPlatformCaller(CurrentUserContext.required())) {
            throw new AccessDeniedException(SharedConstants.ACCESS_DENIED);
        }
    }
}
