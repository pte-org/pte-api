package com.pte.proctoring.internal.service;

import com.pte.proctoring.dto.incident.IncidentHistoryItemView;
import com.pte.proctoring.internal.constant.ExamIncidentConstants;
import com.pte.proctoring.internal.policy.IncidentAccessContext;
import com.pte.shared.audit.AuditChange;
import com.pte.shared.audit.AuditHistoryEntry;
import com.pte.shared.audit.AuditHistoryReader;
import com.pte.shared.audit.AuditLogChangeWriter;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The handling history of a reported incident. It is not a table of its own: every change is one audit row whose
 * after-state carries what the history view needs (who, what, from/to status, notes), and the view is read back
 * from the audit trail oldest first.
 */
@Component
class IncidentHistoryLog {

    private static final int HISTORY_PAGE_SIZE = 100;

    private final AuditLogChangeWriter auditWriter;
    private final AuditHistoryReader auditReader;
    private final JsonMapper jsonMapper;

    IncidentHistoryLog(AuditLogChangeWriter auditWriter, AuditHistoryReader auditReader, JsonMapper jsonMapper) {
        this.auditWriter = auditWriter;
        this.auditReader = auditReader;
        this.jsonMapper = jsonMapper;
    }

    /** What one history row says; serialized as the audit after-state. */
    record Step(String action, String fromStatus, String toStatus, String notes, String actorName,
                String actorRole) {
    }

    void record(CurrentUser caller, UUID incidentPublicId, IncidentAccessContext access, String action,
                String fromStatus, String toStatus, String notes) {
        Step step = new Step(action, fromStatus, toStatus, notes, access.actorName(), access.actorRole());
        try {
            auditWriter.record(caller, new AuditChange(ExamIncidentConstants.AUDIT_AGGREGATE_TYPE,
                    incidentPublicId.toString(), ExamIncidentConstants.AUDIT_ACTION,
                    action + ExamIncidentConstants.AUDIT_SUMMARY_SEPARATOR + toStatus, null, null,
                    jsonMapper.writeValueAsString(step)));
        } catch (JacksonException ex) {
            throw new IllegalStateException(ExamIncidentConstants.HISTORY_SERIALIZATION_FAILED, ex);
        }
    }

    List<IncidentHistoryItemView> read(UUID tenantId, UUID incidentPublicId) {
        List<AuditHistoryEntry> newestFirst = new ArrayList<>(auditReader.history(tenantId,
                ExamIncidentConstants.AUDIT_AGGREGATE_TYPE, incidentPublicId.toString(),
                ExamIncidentConstants.AUDIT_ACTION, 0, HISTORY_PAGE_SIZE).data());
        Collections.reverse(newestFirst);
        return newestFirst.stream().map(entry -> toView(incidentPublicId, entry)).toList();
    }

    private IncidentHistoryItemView toView(UUID incidentPublicId, AuditHistoryEntry entry) {
        try {
            Step step = jsonMapper.readValue(entry.afterStateJson(), Step.class);
            return new IncidentHistoryItemView(entry.publicId().toString(), incidentPublicId, step.actorName(),
                    step.actorRole(), step.action(), step.fromStatus(), step.toStatus(), step.notes(),
                    entry.createdAt());
        } catch (JacksonException ex) {
            throw new IllegalStateException(ExamIncidentConstants.HISTORY_SERIALIZATION_FAILED, ex);
        }
    }
}
