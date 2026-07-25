package com.pte.authoring.mapper;

import com.pte.authoring.domain.ExamSnapshot;
import com.pte.authoring.domain.SnapshotItem;
import com.pte.authoring.dto.response.SnapshotContentResponse;
import com.pte.authoring.dto.response.SnapshotResponse;

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
                snapshot.getTenantId(),
                items);
    }

    private static SnapshotResponse.Item toItem(SnapshotItem item) {
        return new SnapshotResponse.Item(
                item.getOrderIndex(), item.getSection().name(), item.getPteTaskType().name(), item.getTitle());
    }

    /** Full-fidelity mapping for the internal service-to-service surface only. */
    public static SnapshotContentResponse toContentResponse(ExamSnapshot snapshot) {
        List<SnapshotContentResponse.Item> items = snapshot.getItems().stream()
                .map(SnapshotMapper::toContentItem)
                .toList();
        return new SnapshotContentResponse(
                snapshot.getPublicId(), snapshot.getName(), snapshot.getVersion(), snapshot.getTenantId(), items);
    }

    private static SnapshotContentResponse.Item toContentItem(SnapshotItem item) {
        return new SnapshotContentResponse.Item(
                item.getOrderIndex(),
                item.getSection().name(),
                item.getPteTaskType().name(),
                item.getTitle(),
                item.getPromptText(),
                item.getAudioPromptRef(),
                item.getImagePromptRef(),
                item.getReferenceAnswerText(),
                item.getCorrectAnswerText(),
                item.getMinWordCount(),
                item.getMaxWordCount(),
                item.getOptionsJson());
    }
}
