package com.pte.tenancy.internal.service;

import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.domain.enums.TenantStatus;
import com.pte.tenancy.internal.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/** Reactivates only bounded suspensions; indefinite suspensions need an admin action. */
@Service
public class TenantSuspensionExpirationService {

    private static final Logger log = LoggerFactory.getLogger(TenantSuspensionExpirationService.class);

    private final TenantRepository tenantRepository;
    private final Clock clock;

    public TenantSuspensionExpirationService(TenantRepository tenantRepository, Clock clock) {
        this.tenantRepository = tenantRepository;
        this.clock = clock != null ? clock : Clock.systemUTC();
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
            reactivated++;
        }
        if (reactivated > 0) {
            log.info("Reactivated {} tenants after bounded suspension", reactivated);
        }
        return reactivated;
    }
}
