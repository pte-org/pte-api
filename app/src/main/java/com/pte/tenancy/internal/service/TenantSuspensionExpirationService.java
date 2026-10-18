package com.pte.tenancy.internal.service;

import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.domain.enums.TenantStatus;
import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.tenancy.internal.repository.TenantRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Reactivates only bounded suspensions; indefinite suspensions need an admin action. */
@Service
public class TenantSuspensionExpirationService {

    private static final Logger log = LoggerFactory.getLogger(TenantSuspensionExpirationService.class);

    private final TenantRepository tenantRepository;
    private final Clock clock;
    private final AuditLogService auditLogService;

    public TenantSuspensionExpirationService(TenantRepository tenantRepository, Clock clock) {
        this(tenantRepository, clock, null);
    }

    @Autowired
    public TenantSuspensionExpirationService(TenantRepository tenantRepository, Clock clock,
            AuditLogService auditLogService) {
        this.tenantRepository = tenantRepository;
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.auditLogService = auditLogService;
    }

    @Scheduled(cron = "${tenancy.suspension-expiration.cron:0 * * * * *}")
    @Transactional
    public int reactivateDueTenants() {
        Instant now = clock.instant();
        int reactivated = 0;
        for (Tenant tenant : tenantRepository
                .findByStatusAndSuspendedUntilIsNotNullAndSuspendedUntilLessThanEqualAndDeletedFalse(
                        TenantStatus.SUSPENDED, now)) {
            tenant.reactivate();
            if (auditLogService != null) {
                auditLogService.record(
                        new CurrentUser(TenancyConstants.SYSTEM_ACTOR_USER_ID, tenant.getPublicId(), List.of("SYSTEM")),
                        TenancyConstants.AGGREGATE_TENANT, tenant.getPublicId().toString(),
                        TenancyConstants.EVENT_TENANT_REACTIVATED,
                        TenancyConstants.AUDIT_TENANT_REACTIVATED_SUMMARY);
            }
            reactivated++;
        }
        if (reactivated > 0) {
            log.info("Reactivated {} tenants after bounded suspension", reactivated);
        }
        return reactivated;
    }
}
