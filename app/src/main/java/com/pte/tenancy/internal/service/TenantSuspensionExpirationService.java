package com.pte.tenancy.internal.service;

import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Reactivates only bounded suspensions; indefinite suspensions need an admin action. */
@Service
public class TenantSuspensionExpirationService {

    private static final Logger log = LoggerFactory.getLogger(TenantSuspensionExpirationService.class);

    private final OrganizationRepository organizationRepository;
    private final Clock clock;
    private final AuditLogService auditLogService;
    private final TransactionOperations transactions;

    public TenantSuspensionExpirationService(OrganizationRepository organizationRepository, Clock clock) {
        this(organizationRepository, clock, null, TransactionOperations.withoutTransaction());
    }

    public TenantSuspensionExpirationService(OrganizationRepository organizationRepository, Clock clock,
            AuditLogService auditLogService) {
        this(organizationRepository, clock, auditLogService, TransactionOperations.withoutTransaction());
    }

    @Autowired
    public TenantSuspensionExpirationService(OrganizationRepository organizationRepository, Clock clock,
            AuditLogService auditLogService, PlatformTransactionManager transactionManager) {
        this(organizationRepository, clock, auditLogService, new TransactionTemplate(transactionManager));
    }

    TenantSuspensionExpirationService(OrganizationRepository organizationRepository, Clock clock,
            AuditLogService auditLogService, TransactionOperations transactions) {
        this.organizationRepository = organizationRepository;
        this.clock = clock;
        this.auditLogService = auditLogService;
        this.transactions = transactions;
    }

    /** One transaction per tenant: a tenant that cannot be reactivated must not keep the others suspended. */
    @Scheduled(cron = "${tenancy.suspension-expiration.cron:0 * * * * *}")
    public int reactivateDueTenants() {
        Instant now = clock.instant();
        int reactivated = 0;
        for (Organization due : organizationRepository
                .findByStatusAndSuspendedUntilIsNotNullAndSuspendedUntilLessThanEqualAndDeletedFalse(
                        OrganizationStatus.SUSPENDED, now)) {
            UUID tenantPublicId = due.getPublicId();
            try {
                if (Boolean.TRUE.equals(transactions.execute(status -> reactivateIfStillDue(tenantPublicId, now)))) {
                    reactivated++;
                }
            } catch (RuntimeException exception) {
                log.warn("Unable to reactivate tenant {} after its suspension ended", tenantPublicId, exception);
            }
        }
        if (reactivated > 0) {
            log.info("Reactivated {} tenants after bounded suspension", reactivated);
        }
        return reactivated;
    }

    private boolean reactivateIfStillDue(UUID tenantPublicId, Instant now) {
        Organization tenant = organizationRepository.findByPublicId(tenantPublicId).orElse(null);
        if (tenant == null || tenant.isDeleted() || tenant.getStatus() != OrganizationStatus.SUSPENDED
                || tenant.getSuspendedUntil() == null || tenant.getSuspendedUntil().isAfter(now)) {
            return false;
        }
        tenant.reactivate();
        if (auditLogService != null) {
            auditLogService.record(
                    new CurrentUser(TenancyConstants.SYSTEM_ACTOR_USER_ID, tenant.getPublicId(), List.of("SYSTEM")),
                    TenancyConstants.AGGREGATE_TENANT, tenant.getPublicId().toString(),
                    TenancyConstants.EVENT_TENANT_REACTIVATED,
                    TenancyConstants.AUDIT_TENANT_REACTIVATED_SUMMARY);
        }
        return true;
    }
}
