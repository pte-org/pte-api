package com.pte.reporting.controller;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import com.pte.reporting.service.RebuildOrchestrationService;
import com.pte.reporting.service.RebuildOrchestrationService.RebuildSummary;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operator-invoked, single-tenant recovery path (rabbitmq-outbox-migration
 * Phase 9) — a host re-derives their own tenant's read model from
 * exam-delivery/scoring's source-of-truth data. Tenant scope comes from the
 * caller's own validated JWT, never a request parameter — a host can only
 * ever rebuild their own tenant. See {@code InternalRebuildController} for
 * the separate new-instance full-bootstrap (all-tenant) path, which this
 * endpoint can never reach into.
 */
@RestController
@RequestMapping("/reports")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class RebuildController {

    private final RebuildOrchestrationService rebuildOrchestrationService;

    public RebuildController(RebuildOrchestrationService rebuildOrchestrationService) {
        this.rebuildOrchestrationService = rebuildOrchestrationService;
    }

    @PostMapping("/rebuild")
    public ApiResponse<RebuildSummary> rebuild() {
        CurrentUser currentUser = CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
        return ApiResponse.success(rebuildOrchestrationService.rebuild(currentUser.tenantId()));
    }
}
