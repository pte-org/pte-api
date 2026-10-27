package com.pte.assessment.internal.mapper;

import com.pte.assessment.domain.ExamVersion;
import com.pte.assessment.domain.ExamVersionItem;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.itembank.domain.enums.PteSection;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SnapshotMapper#toContentResponse(ExamSnapshot)},
 * specifically verifying that the {@code sourceQuestionPublicId} added for
 * the report-question feature (plans/report-question-ticket) is correctly
 * propagated from {@link SnapshotItem} to {@link SnapshotContentResponse.Item}.
 */
class SnapshotMapperTest {

    @Test
    void toContentResponse_sourceQuestionPublicId_isPropagatedWhenSet() {
        UUID questionId = UUID.randomUUID();
        ExamVersion snapshot = snapshotWithItem(questionId);

        SnapshotContentResponse response = SnapshotMapper.toContentResponse(snapshot);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sourceQuestionPublicId()).isEqualTo(questionId);
    }

    @Test
    void toContentResponse_sourceQuestionPublicId_isPreservedAsNullWhenAbsent() {
        ExamVersion snapshot = snapshotWithItem(null);

        SnapshotContentResponse response = SnapshotMapper.toContentResponse(snapshot);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).sourceQuestionPublicId()).isNull();
    }

    @Test
    void toContentResponse_multipleItems_eachCarriesOwnSourceQuestionPublicId() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        ExamVersion snapshot = new ExamVersion();
        snapshot.setName("Multi-item Snapshot");
        snapshot.setVersion(1);
        snapshot.setExamDefinitionPublicId(UUID.randomUUID());
        snapshot.setTenantId(UUID.randomUUID());
        snapshot.addItem(buildItem(0, firstId));
        snapshot.addItem(buildItem(1, secondId));

        SnapshotContentResponse response = SnapshotMapper.toContentResponse(snapshot);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).sourceQuestionPublicId()).isEqualTo(firstId);
        assertThat(response.items().get(1).sourceQuestionPublicId()).isEqualTo(secondId);
    }

    @Test
    void toContentResponse_mixedNullAndNonNullSourceIds_mappedCorrectly() {
        UUID presentId = UUID.randomUUID();

        ExamVersion snapshot = new ExamVersion();
        snapshot.setName("Mixed Snapshot");
        snapshot.setVersion(1);
        snapshot.setExamDefinitionPublicId(UUID.randomUUID());
        snapshot.setTenantId(UUID.randomUUID());
        snapshot.addItem(buildItem(0, presentId));
        snapshot.addItem(buildItem(1, null));

        SnapshotContentResponse response = SnapshotMapper.toContentResponse(snapshot);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).sourceQuestionPublicId()).isEqualTo(presentId);
        assertThat(response.items().get(1).sourceQuestionPublicId()).isNull();
    }

    // --- helpers ---

    private ExamVersion snapshotWithItem(UUID sourceQuestionPublicId) {
        ExamVersion snapshot = new ExamVersion();
        snapshot.setName("Test Snapshot");
        snapshot.setVersion(1);
        snapshot.setExamDefinitionPublicId(UUID.randomUUID());
        snapshot.setTenantId(UUID.randomUUID());
        snapshot.addItem(buildItem(0, sourceQuestionPublicId));
        return snapshot;
    }

    private ExamVersionItem buildItem(int orderIndex, UUID sourceQuestionPublicId) {
        ExamVersionItem item = new ExamVersionItem();
        item.setOrderIndex(orderIndex);
        item.setSection(PteSection.READING);
        item.setTaskTypeKey("MC_READING_SINGLE");
        item.setTitle("Sample Question " + orderIndex);
        item.setSourceQuestionPublicId(sourceQuestionPublicId);
        return item;
    }
}
