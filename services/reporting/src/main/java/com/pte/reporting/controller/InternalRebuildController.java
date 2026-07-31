package com.pte.reporting.controller;

import com.pte.common.security.InternalServiceAuth;
import com.pte.common.web.ApiResponse;
import com.pte.reporting.service.RebuildOrchestrationService;
import com.pte.reporting.service.RebuildOrchestrationService.RebuildSummary;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * New-instance full-bootstrap rebuild (rabbitmq-outbox-migration Phase 9) — a
 * fresh reporting instance with an empty DB has no single tenant's JWT to
 * drive tenant scoping, so this is a distinct, more-privileged trigger:
 * requires {@link InternalServiceAuth#AUTHORITY_INTERNAL_SERVICE_BOOTSTRAP}
 * (BOTH the base internal-service key AND the separate bootstrap key — see
 * {@code InternalBootstrapKeyFilter}), invoked by an operator/ops script, never
 * by another service in the normal request path. Rebuilds ALL tenants.
 */
@RestController
@RequestMapping("/internal/rebuild")
@PreAuthorize("hasRole('INTERNAL_SERVICE_BOOTSTRAP')")
public class InternalRebuildController {

    private final RebuildOrchestrationService rebuildOrchestrationService;

    public InternalRebuildController(RebuildOrchestrationService rebuildOrchestrationService) {
        this.rebuildOrchestrationService = rebuildOrchestrationService;
    }

    @PostMapping("/bootstrap")
    public ApiResponse<RebuildSummary> bootstrap() {
        return ApiResponse.success(rebuildOrchestrationService.rebuild(null));
    }
}
