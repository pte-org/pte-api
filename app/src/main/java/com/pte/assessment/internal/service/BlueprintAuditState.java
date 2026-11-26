package com.pte.assessment.internal.service;

import com.pte.assessment.domain.BlueprintItem;
import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.shared.audit.AuditLogChangeWriter;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.audit.AuditStateChange;
import com.pte.shared.security.CurrentUser;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Business snapshot of a blueprint (name and ordered question slots) for the audit trail. */
record BlueprintAuditState(String name, List<ItemState> items) {

    private static final int SUMMARY_MAX_LENGTH = 500;

    record ItemState(int orderIndex, String section, UUID questionPublicId) {
    }

    static BlueprintAuditState of(ExamBlueprint blueprint) {
        return new BlueprintAuditState(blueprint.getName(), blueprint.getItems().stream()
                .map(BlueprintAuditState::itemOf)
                .sorted(Comparator.comparingInt(ItemState::orderIndex))
                .toList());
    }

    /**
     * Writes the command with before/after snapshots, or only the summary when no change writer is wired (the
     * unit-test constructors pass null).
     */
    static void write(AuditLogChangeWriter writer, AuditLogService fallback, CurrentUser caller, String action,
            ExamBlueprint blueprint, String summary, BlueprintAuditState before) {
        if (caller == null) {
            return;
        }
        String aggregateId = String.valueOf(blueprint.getPublicId());
        String text = summary.length() <= SUMMARY_MAX_LENGTH ? summary : summary.substring(0, SUMMARY_MAX_LENGTH);
        if (writer != null) {
            writer.recordState(caller, new AuditStateChange(AssessmentConstants.AUDIT_AGGREGATE_BLUEPRINT,
                    aggregateId, blueprint.getName(), null, action, text, before, of(blueprint)));
        } else if (fallback != null) {
            fallback.record(caller, AssessmentConstants.AUDIT_AGGREGATE_BLUEPRINT, aggregateId, action, text);
        }
    }

    private static ItemState itemOf(BlueprintItem item) {
        return new ItemState(item.getOrderIndex(), item.getSection() == null ? null : item.getSection().name(),
                item.getQuestionPublicId());
    }
}
