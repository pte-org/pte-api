package com.pte.assessment.internal.service.definition;

import com.pte.assessment.domain.ExamCefrTarget;
import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.ExamDefinitionItem;
import com.pte.assessment.domain.ExamTimeFrame;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Business snapshot of a template for the audit trail. The {@code runtime_*} item columns are left out: they are
 * resolved from the task type's runtime profile, nobody edits them on the template, and they would quadruple the
 * stored size (about 23 KB instead of 5 KB for a full 22-task template).
 */
record ExamDefinitionAuditState(
        String name,
        String templatePolicy,
        UUID rubricVersionPublicId,
        ExamTimeFrame timeFrame,
        List<ExamCefrTarget> cefrTargets,
        List<ItemState> items) {

    record ItemState(
            int sequence,
            String section,
            String taskTypeKey,
            int minCount,
            int maxCount,
            int prepSeconds,
            int responseSeconds,
            String scoringMethod,
            BigDecimal overallWeight,
            BigDecimal speakingWeight,
            BigDecimal writingWeight,
            BigDecimal readingWeight,
            BigDecimal listeningWeight) {
    }

    static ExamDefinitionAuditState of(ExamDefinition template) {
        return new ExamDefinitionAuditState(
                template.getName(),
                template.getTemplatePolicy() == null ? null : template.getTemplatePolicy().name(),
                template.getRubricVersionPublicId(),
                template.getTimeFrame(),
                List.copyOf(template.getCefrTargets()),
                template.getItems().stream().map(ExamDefinitionAuditState::itemOf)
                        .sorted(Comparator.comparingInt(ItemState::sequence)).toList());
    }

    private static ItemState itemOf(ExamDefinitionItem item) {
        return new ItemState(item.getSequence(), item.getSection(), item.getTaskTypeKey(), item.getMinCount(),
                item.getMaxCount(), item.getPrepSeconds(), item.getResponseSeconds(),
                item.getScoringMethod() == null ? null : item.getScoringMethod().name(),
                item.getOverallWeight(), item.getSpeakingWeight(), item.getWritingWeight(),
                item.getReadingWeight(), item.getListeningWeight());
    }
}
