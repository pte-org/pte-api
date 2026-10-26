package com.pte.proctoring.internal.service;

import com.pte.proctoring.dto.incident.IncidentHistoryItemView;
import com.pte.proctoring.internal.policy.IncidentAccessContext;
import com.pte.shared.audit.AuditChange;
import com.pte.shared.audit.AuditHistoryEntry;
import com.pte.shared.audit.AuditHistoryReader;
import com.pte.shared.audit.AuditLogChangeWriter;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentHistoryLogTest {

    @Mock
    private AuditLogChangeWriter auditWriter;

    @Mock
    private AuditHistoryReader auditReader;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private IncidentHistoryLog log;
    private final UUID incidentId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));

    @BeforeEach
    void setUp() {
        log = new IncidentHistoryLog(auditWriter, auditReader, jsonMapper);
    }

    @Test
    void record_writesOneAuditRowWhoseAfterStateCarriesTheStep() throws Exception {
        IncidentAccessContext access = new IncidentAccessContext(tenantId, caller.userId(), "HOST_ADMIN", "Host", false);

        log.record(caller, incidentId, access, "UPDATE_STATUS", "NEW", "IN_PROGRESS", "on it");

        ArgumentCaptor<AuditChange> change = ArgumentCaptor.forClass(AuditChange.class);
        verify(auditWriter).record(eq(caller), change.capture());
        assertThat(change.getValue().aggregateType()).isEqualTo("EXAM_INCIDENT");
        assertThat(change.getValue().aggregateId()).isEqualTo(incidentId.toString());
        assertThat(change.getValue().action()).isEqualTo("INCIDENT_CHANGED");
        IncidentHistoryLog.Step step = jsonMapper.readValue(change.getValue().afterStateJson(),
                IncidentHistoryLog.Step.class);
        assertThat(step).isEqualTo(new IncidentHistoryLog.Step("UPDATE_STATUS", "NEW", "IN_PROGRESS", "on it",
                "Host", "HOST_ADMIN"));
    }

    @Test
    void read_returnsTheStepsOldestFirstWithTheirActor() throws Exception {
        UUID createdId = UUID.randomUUID();
        UUID changedId = UUID.randomUUID();
        AuditHistoryEntry newer = entry(changedId, new IncidentHistoryLog.Step("UPDATE_STATUS", "NEW",
                "RESOLVED", "done", "Host", "HOST_ADMIN"), Instant.parse("2026-09-28T04:05:00Z"));
        AuditHistoryEntry older = entry(createdId, new IncidentHistoryLog.Step("CREATE", null, "NEW", "noise",
                "Proctor", "PROCTOR"), Instant.parse("2026-09-28T04:00:00Z"));
        when(auditReader.history(eq(tenantId), eq("EXAM_INCIDENT"), eq(incidentId.toString()),
                eq("INCIDENT_CHANGED"), eq(0), eq(100))).thenReturn(new PagedResult<>(List.of(newer, older),
                new PageMeta(0, 100, 2, 1, true, true, false, false)));

        List<IncidentHistoryItemView> history = log.read(tenantId, incidentId);

        assertThat(history).extracting(IncidentHistoryItemView::action).containsExactly("CREATE", "UPDATE_STATUS");
        assertThat(history.get(0).actorName()).isEqualTo("Proctor");
        assertThat(history.get(0).fromStatus()).isNull();
        assertThat(history.get(1).toStatus()).isEqualTo("RESOLVED");
        assertThat(history.get(1).notes()).isEqualTo("done");
        assertThat(history.get(1).incidentPublicId()).isEqualTo(incidentId);
    }

    private AuditHistoryEntry entry(UUID id, IncidentHistoryLog.Step step, Instant at) throws Exception {
        return new AuditHistoryEntry(id, UUID.randomUUID(), "HOST_ADMIN", null, "summary", null,
                jsonMapper.writeValueAsString(step), at);
    }
}
