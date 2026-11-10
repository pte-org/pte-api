package com.pte.assessment.internal.mapper;

import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.ExamDefinitionItem;
import com.pte.assessment.domain.ExamTimeFrame;
import com.pte.assessment.dto.response.CefrTargetResponse;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.dto.response.ExamTimeFrameResponse;

public final class ExamDefinitionMapper {

    private ExamDefinitionMapper() {
    }

    public static ExamDefinitionResponse toResponse(ExamDefinition template) {
        return new ExamDefinitionResponse(
                template.getPublicId(),
                template.getCode(),
                template.getVersion(),
                template.getName(),
                template.getStatus().name(),
                template.getRejectionReason(),
                template.getItems().stream().map(ExamDefinitionMapper::toItemResponse).toList(),
                template.getTemplatePolicy() == null ? "STANDARD_PTE" : template.getTemplatePolicy().name(),
                template.getTenantId(),
                template.getBaseDefinitionPublicId(),
                template.getCreatedAt(),
                template.getUpdatedAt(),
                template.getAuthorUserPublicId(),
                null,
                toTimeFrameResponse(template.getTimeFrame()),
                null,
                java.util.List.of(),
                template.getCefrTargets().stream()
                        .map(target -> new CefrTargetResponse(target.level(), target.percent())).toList(),
                template.getRubricVersionPublicId());
    }

    private static ExamTimeFrameResponse toTimeFrameResponse(ExamTimeFrame frame) {
        if (frame == null) {
            return null;
        }
        return new ExamTimeFrameResponse(frame.speakingWritingMinSeconds(), frame.speakingWritingMaxSeconds(),
                frame.readingMinSeconds(), frame.readingMaxSeconds(), frame.listeningMinSeconds(),
                frame.listeningMaxSeconds(), frame.totalMaxSeconds());
    }

    private static ExamDefinitionItemResponse toItemResponse(ExamDefinitionItem item) {
        return new ExamDefinitionItemResponse(
                item.getTaskType() == null ? null : TaskTypeCodeCompatibility.normalizeForLookup(item.getTaskType()),
                item.getSection(),
                item.getSequence(),
                item.getMinCount(),
                item.getMaxCount(),
                item.getPrepSeconds(),
                item.getResponseSeconds(),
                item.getScoringMethod().name(),
                item.getOverallWeight(),
                item.getSpeakingWeight(),
                item.getWritingWeight(),
                item.getReadingWeight(),
                item.getListeningWeight(),
                item.pinnedRuntimeProfile(), item.getTaskTypeKey() == null
                        ? item.getTaskType() : item.getTaskTypeKey());
    }
}
