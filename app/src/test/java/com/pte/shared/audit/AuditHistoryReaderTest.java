package com.pte.shared.audit;

import com.pte.shared.audit.domain.AuditLog;
import com.pte.shared.audit.internal.repository.AuditLogRepository;
import com.pte.shared.web.PagedResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The history of one aggregate: tenant-scoped, one action only, newest first, with the request id and states. */
@DataJpaTest
@Import(AuditHistoryReader.class)
class AuditHistoryReaderTest {

    private static final String AGGREGATE_TYPE = "SESSION_ATTENDANCE";
    private static final String ACTION = "ATTENDANCE_RECORDED";

    @Autowired
    private AuditLogRepository repository;

    @Autowired
    private AuditHistoryReader reader;

    private AuditLog entry(UUID tenantId, String aggregateId, String action, String summary, UUID requestId) {
        AuditLog log = new AuditLog();
        log.setTenantId(tenantId);
        log.setActorUserId(UUID.randomUUID());
        log.setActorRole("HOST_ADMIN");
        log.setAggregateType(AGGREGATE_TYPE);
        log.setAggregateId(aggregateId);
        log.setAction(action);
        log.setSummary(summary);
        log.setRequestPublicId(requestId);
        log.setBeforeState("{\"status\":\"UNMARKED\"}");
        log.setAfterState("{\"status\":\"PRESENT\"}");
        return repository.saveAndFlush(log);
    }

    @Test
    void returnsOnlyThisTenantsAggregateAndActionNewestFirst() {
        UUID tenantId = UUID.randomUUID();
        UUID firstRequest = UUID.randomUUID();
        UUID secondRequest = UUID.randomUUID();
        entry(tenantId, "s:1", ACTION, "first", firstRequest);
        entry(tenantId, "s:1", ACTION, "second", secondRequest);
        entry(tenantId, "s:1", "IDENTITY_VERIFIED", "other action", UUID.randomUUID());
        entry(tenantId, "s:2", ACTION, "other student", UUID.randomUUID());
        entry(UUID.randomUUID(), "s:1", ACTION, "other tenant", UUID.randomUUID());

        PagedResult<AuditHistoryEntry> page = reader.history(tenantId, AGGREGATE_TYPE, "s:1", ACTION, 0, 20);

        assertThat(page.data()).extracting(AuditHistoryEntry::summary).containsExactly("second", "first");
        assertThat(page.data().get(0).requestPublicId()).isEqualTo(secondRequest);
        assertThat(page.data().get(0).beforeStateJson()).contains("UNMARKED");
        assertThat(page.data().get(0).afterStateJson()).contains("PRESENT");
        assertThat(page.meta().totalElements()).isEqualTo(2);
    }

    @Test
    void clampsThePageSize() {
        UUID tenantId = UUID.randomUUID();
        for (int index = 0; index < 3; index++) {
            entry(tenantId, "s:3", ACTION, "entry " + index, UUID.randomUUID());
        }

        PagedResult<AuditHistoryEntry> defaulted = reader.history(tenantId, AGGREGATE_TYPE, "s:3", ACTION, -4, 0);
        PagedResult<AuditHistoryEntry> clamped = reader.history(tenantId, AGGREGATE_TYPE, "s:3", ACTION, 0, 500);

        assertThat(defaulted.meta().page()).isZero();
        assertThat(defaulted.meta().size()).isEqualTo(20);
        assertThat(clamped.meta().size()).isEqualTo(100);
    }
}
