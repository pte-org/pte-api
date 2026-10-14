package com.pte.shared.audit;

import com.pte.shared.audit.domain.AuditLog;
import com.pte.shared.audit.dto.AuditLogResponse;
import com.pte.shared.audit.internal.repository.AuditLogRepository;
import com.pte.shared.security.CurrentUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link AuditLogService#recent}: newest first, size cap, soft delete and tenant isolation on the embedded database. */
@DataJpaTest
@Import(AuditLogService.class)
class AuditLogServiceRecentTest {

    private static final Instant BASE = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private AuditLogService service;

    @Autowired
    private AuditLogRepository repository;

    @Autowired
    private EntityManager em;

    private AuditLog log(UUID tenant, int minuteOffset, boolean deleted) {
        AuditLog auditLog = new AuditLog();
        auditLog.setTenantId(tenant);
        auditLog.setActorUserId(UUID.randomUUID());
        auditLog.setAggregateType("CLASS");
        auditLog.setAggregateId(UUID.randomUUID().toString());
        auditLog.setAction("ACTION_" + minuteOffset);
        auditLog.setSummary("summary " + minuteOffset);
        auditLog.setDeleted(deleted);
        AuditLog saved = repository.saveAndFlush(auditLog);
        em.createQuery("update AuditLog a set a.createdAt = :createdAt where a.id = :id")
                .setParameter("createdAt", BASE.plusSeconds(60L * minuteOffset)).setParameter("id", saved.getId())
                .executeUpdate();
        em.clear();
        return saved;
    }

    @Test
    void returnsTheNewestEntriesFirst() {
        UUID tenant = UUID.randomUUID();
        log(tenant, 2, false);
        log(tenant, 5, false);
        log(tenant, 1, false);
        log(tenant, 4, false);
        log(tenant, 3, false);

        List<AuditLogResponse> recent = service.recent(tenant, 8);

        assertThat(recent).extracting(AuditLogResponse::action)
                .containsExactly("ACTION_5", "ACTION_4", "ACTION_3", "ACTION_2", "ACTION_1");
        assertThat(recent).extracting(AuditLogResponse::createdAt).isSortedAccordingTo(
                java.util.Comparator.reverseOrder());
    }

    @Test
    void sizeCapsTheResultAndKeepsTheNewest() {
        UUID tenant = UUID.randomUUID();
        for (int i = 1; i <= 12; i++) {
            log(tenant, i, false);
        }

        List<AuditLogResponse> recent = service.recent(tenant, 8);

        assertThat(recent).hasSize(8);
        assertThat(recent.get(0).action()).isEqualTo("ACTION_12");
        assertThat(recent.get(7).action()).isEqualTo("ACTION_5");
    }

    @Test
    void sizeAboveTheHardMaximumIsClampedToOneHundred() {
        UUID tenant = UUID.randomUUID();
        for (int i = 1; i <= 105; i++) {
            log(tenant, i, false);
        }

        assertThat(service.recent(tenant, 10_000)).hasSize(100);
    }

    @Test
    void nonPositiveSizeIsClampedToOne() {
        UUID tenant = UUID.randomUUID();
        log(tenant, 1, false);
        log(tenant, 2, false);

        assertThat(service.recent(tenant, 0)).extracting(AuditLogResponse::action).containsExactly("ACTION_2");
        assertThat(service.recent(tenant, -5)).hasSize(1);
    }

    @Test
    void softDeletedEntriesAreExcluded() {
        UUID tenant = UUID.randomUUID();
        log(tenant, 1, false);
        log(tenant, 9, true); // newest, but deleted
        log(tenant, 2, false);

        assertThat(service.recent(tenant, 8)).extracting(AuditLogResponse::action)
                .containsExactly("ACTION_2", "ACTION_1");
    }

    @Test
    void otherTenantsAndTenantlessPlatformEntriesNeverAppear() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        log(tenantA, 1, false);
        log(tenantB, 50, false);
        log(tenantB, 51, false);
        log(null, 60, false); // platform-level, tenant is null

        assertThat(service.recent(tenantA, 8)).extracting(AuditLogResponse::action).containsExactly("ACTION_1");
        assertThat(service.recent(tenantB, 8)).hasSize(2);
    }

    @Test
    void aTenantWithoutEntriesGetsAnEmptyList() {
        log(UUID.randomUUID(), 1, false);

        assertThat(service.recent(UUID.randomUUID(), 8)).isEmpty();
    }

    @Test
    void mapsEveryFieldOfTheEntry() {
        UUID tenant = UUID.randomUUID();
        AuditLog saved = log(tenant, 3, false);

        AuditLogResponse response = service.recent(tenant, 8).get(0);

        assertThat(response.publicId()).isEqualTo(saved.getPublicId());
        assertThat(response.actorUserId()).isEqualTo(saved.getActorUserId());
        assertThat(response.aggregateType()).isEqualTo("CLASS");
        assertThat(response.aggregateId()).isEqualTo(saved.getAggregateId());
        assertThat(response.action()).isEqualTo("ACTION_3");
        assertThat(response.summary()).isEqualTo("summary 3");
        assertThat(response.createdAt()).isEqualTo(BASE.plusSeconds(180));
    }

    @Test
    void searchUsesResourceIdentityAndDoesNotMatchInternalAggregateId() {
        UUID tenant = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        AuditLog saved = log(tenant, 3, false);
        saved.setAggregateId(aggregateId.toString());
        saved.setResourceName("Mock exam");
        saved.setResourceCode("HCM-261014-AB12");
        repository.saveAndFlush(saved);

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenant, List.of("HOST_ADMIN"));

        assertThat(service.list(caller, "CLASS", aggregateId.toString(), 0, 20).data()).isEmpty();
        assertThat(service.list(caller, "CLASS", "HCM-261014-AB12", 0, 20).data())
                .extracting(AuditLogResponse::resourceName)
                .containsExactly("Mock exam");
    }
}
