package com.pte.assessment.internal.mapper;

import com.pte.assessment.domain.ExamSnapshot;
import com.pte.assessment.domain.SnapshotItem;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.itembank.TaskRuntimeContractConstants;

import java.util.List;

public final class SnapshotMapper {

    private SnapshotMapper() {
    }

    public static SnapshotResponse toResponse(ExamSnapshot snapshot) {
        List<SnapshotResponse.Item> items = snapshot.getItems().stream()
                .map(SnapshotMapper::toItem)
                .toList();
        return new SnapshotResponse(
                snapshot.getPublicId(),
                snapshot.getName(),
                snapshot.getVersion(),
                snapshot.getSourceBlueprintPublicId(),
                snapshot.getScoreTemplatePublicId(),
                snapshot.getScoreTemplateVersion(),
                snapshot.getTenantId(),
                items);
    }

    private static SnapshotResponse.Item toItem(SnapshotItem item) {
        String taskTypeKey = item.getTaskTypeKey() != null ? item.getTaskTypeKey()
                : item.getPteTaskType() == null ? item.getTaskTypeCode() : item.getPteTaskType().name();
        String legacyTaskType = item.getPteTaskType() == null ? null : item.getPteTaskType().name();
        return new SnapshotResponse.Item(
                item.getOrderIndex(), item.getSection().name(), legacyTaskType, item.getTitle(), taskTypeKey,
                item.getTaskTypeDisplayName(),
                item.getTaskTypeCode() != null ? item.getTaskTypeCode() : taskTypeKey,
                item.runtimeProfile(), item.getRuntimeMappingVersion(), runtimeMappingStatus(item));
    }

    /** Full-fidelity mapping for the trusted application-call surface only. */
    public static SnapshotContentResponse toContentResponse(ExamSnapshot snapshot) {
        List<SnapshotContentResponse.Item> items = snapshot.getItems().stream()
                .map(SnapshotMapper::toContentItem)
                .toList();
        return new SnapshotContentResponse(
                snapshot.getPublicId(), snapshot.getName(), snapshot.getVersion(),
                snapshot.getScoreTemplatePublicId(), snapshot.getScoreTemplateVersion(), snapshot.getTenantId(), items);
    }

    private static SnapshotContentResponse.Item toContentItem(SnapshotItem item) {
        String taskTypeKey = item.getTaskTypeKey() != null ? item.getTaskTypeKey()
                : item.getPteTaskType() == null ? item.getTaskTypeCode() : item.getPteTaskType().name();
        String legacyTaskType = item.getPteTaskType() == null ? null : item.getPteTaskType().name();
        return new SnapshotContentResponse.Item(
                item.getOrderIndex(),
                item.getSection().name(),
                legacyTaskType,
                item.getTitle(),
                item.getPromptText(),
                item.getAudioPromptRef(),
                item.getImagePromptRef(),
                item.getReferenceAnswerText(),
                item.getCorrectAnswerText(),
                item.getMinWordCount(),
                item.getMaxWordCount(),
                item.getOptionsJson(),
                taskTypeKey,
                item.getTaskTypeDisplayName(),
                item.getTaskTypeCode() != null ? item.getTaskTypeCode() : taskTypeKey,
                item.runtimeProfile(), item.getRuntimeMappingVersion(), runtimeMappingStatus(item));
    }

    private static String runtimeMappingStatus(SnapshotItem item) {
        return item.hasPartialRuntimeProfile() ? TaskRuntimeContractConstants.MAPPING_STATUS_INCOMPATIBLE
                : item.getRuntimeMappingStatus();
    }
}
