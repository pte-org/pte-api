package com.pte.shared.audit;

import com.pte.shared.audit.domain.AuditLog;
import com.pte.shared.audit.internal.constant.AuditLogConstants;
import com.pte.shared.audit.internal.repository.AuditLogRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** Catalogue edits store business snapshots that a web diff can compare reliably. */
@ExtendWith(MockitoExtension.class)
class AuditStateRecordTest {

    private static final CurrentUser STAFF = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogChangeWriter writer;

    private record Snapshot(String prompt, List<String> options) {
    }

    @Test
    void recordStateStoresBothSnapshotsWithResourceIdentityAndActorRole() {
        writer.recordState(STAFF, new AuditStateChange("QUESTION", "q-1", "Read aloud", "RA-1",
                "QUESTION_UPDATED", "Updated question", new Snapshot("old", List.of("a")),
                new Snapshot("new", List.of("a", "b"))));

        AuditLog saved = saved();
        assertThat(saved.getTenantId()).isNull();
        assertThat(saved.getActorRole()).isEqualTo("ACADEMIC_STAFF");
        assertThat(saved.getResourceName()).isEqualTo("Read aloud");
        assertThat(saved.getResourceCode()).isEqualTo("RA-1");
        assertThat(saved.getBeforeState()).isEqualTo("{\"options\":[\"a\"],\"prompt\":\"old\"}");
        assertThat(saved.getAfterState()).isEqualTo("{\"options\":[\"a\",\"b\"],\"prompt\":\"new\"}");
    }

    @Test
    void creationHasNoBeforeState() {
        writer.recordState(STAFF, new AuditStateChange("QUESTION", "q-1", null, null, "QUESTION_CREATED",
                "Created", null, new Snapshot("new", List.of())));

        AuditLog saved = saved();
        assertThat(saved.getBeforeState()).isNull();
        assertThat(saved.getAfterState()).contains("\"prompt\":\"new\"");
    }

    @Test
    void identicalSnapshotsKeepTheRowButDropTheStates() {
        writer.recordState(STAFF, new AuditStateChange("QUESTION", "q-1", null, null, "QUESTION_UPDATED",
                "Updated", new Snapshot("same", List.of("a")), new Snapshot("same", List.of("a"))));

        AuditLog saved = saved();
        assertThat(saved.getSummary()).isEqualTo("Updated");
        assertThat(saved.getBeforeState()).isNull();
        assertThat(saved.getAfterState()).isNull();
    }

    @Test
    void mapKeysAreSortedSoEqualStatesSerializeIdentically() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("b", 2);
        first.put("a", 1);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("a", 1);
        second.put("b", 2);

        assertThat(AuditStateSerializer.toJson(first)).isEqualTo(AuditStateSerializer.toJson(second));
    }

    @Test
    void oversizedStateBecomesATruncationMarker() {
        String json = AuditStateSerializer.toJson(new Snapshot("x".repeat(AuditLogConstants.STATE_MAX_CHARS), List.of()));

        assertThat(json).startsWith("{\"preview\":").endsWith(",\"truncated\":true}");
        assertThat(json.length()).isLessThan(AuditLogConstants.STATE_PREVIEW_CHARS + 100);
    }

    @Test
    void unserializableStateIsDroppedInsteadOfFailingTheCommand() {
        Object selfReferencing = new Object() {
            @SuppressWarnings("unused")
            public Object getSelf() {
                return this;
            }
        };

        assertThat(AuditStateSerializer.toJson(selfReferencing)).isNull();
    }

    private AuditLog saved() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }
}
