package com.pte.assessment.internal.service;

import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.domain.ExamSnapshot;
import com.pte.assessment.domain.enums.BlueprintStatus;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.dto.response.TemplateSpec;
import com.pte.assessment.internal.exception.BlueprintNotFoundException;
import com.pte.assessment.internal.repository.ExamBlueprintRepository;
import com.pte.assessment.internal.repository.ExamSnapshotRepository;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.dto.response.QuestionFreezeView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Covers assessment snapshot persistence and the seed/weight capture boundary. */
@ExtendWith(MockitoExtension.class)
class SnapshotPublishServiceTest {

    @Mock
    private ExamBlueprintRepository blueprintRepository;
    @Mock
    private ExamSnapshotRepository snapshotRepository;

    private SnapshotPublishService service;

    @BeforeEach
    void setUp() {
        service = new SnapshotPublishService(blueprintRepository, snapshotRepository,
                new AssessmentAccessPolicy(), JsonMapper.builder().build());
    }

    @Test
    void getSummary_unknownSnapshot_throwsNotFound() {
        UUID publicId = UUID.randomUUID();
        when(snapshotRepository.findWithItemsByPublicId(publicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSummary(publicId)).isInstanceOf(BlueprintNotFoundException.class);
    }

    @Test
    void getContent_returnsFullFidelityIncludingAnswerKey() {
        UUID publicId = UUID.randomUUID();
        ExamSnapshot snapshot = new ExamSnapshot();
        snapshot.setName("Mock Test A");
        snapshot.setVersion(1);
        snapshot.setSourceBlueprintPublicId(UUID.randomUUID());
        when(snapshotRepository.findWithItemsByPublicId(publicId)).thenReturn(Optional.of(snapshot));

        var content = service.getContent(publicId);

        assertThat(content.name()).isEqualTo("Mock Test A");
    }

    @Test
    void publishGenerated_capturesSeedWeightsAndFrozenItems() {
        UUID templateId = UUID.randomUUID();
        UUID blueprintId = UUID.randomUUID();
        ExamBlueprint blueprint = new ExamBlueprint();
        blueprint.setPublicId(blueprintId);
        blueprint.setName("Template");
        blueprint.setTenantId(null);
        TemplateSpec spec = new TemplateSpec(templateId, "Template", List.of(
                new TemplateSpec.Section(PteSection.READING, 100, 0,
                        List.of(new TemplateSpec.Slot(PteTaskType.MC_READING_SINGLE, 1, 0)))));
        QuestionFreezeView question = new QuestionFreezeView(
                UUID.randomUUID(), PteTaskType.MC_READING_SINGLE, "title", "prompt",
                null, null, null, "A", null, null, List.of());
        when(snapshotRepository.countBySourceBlueprintPublicId(blueprintId)).thenReturn(0L);
        when(snapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SnapshotResponse response = service.publishGenerated(templateId, 42L, blueprint, spec,
                List.of(new SnapshotPublishService.GeneratedItem(PteSection.READING, 0, question)));

        assertThat(response.items()).hasSize(1);
        assertThat(blueprint.getStatus()).isEqualTo(BlueprintStatus.PUBLISHED);
        var captor = org.mockito.ArgumentCaptor.forClass(ExamSnapshot.class);
        org.mockito.Mockito.verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getTemplatePublicId()).isEqualTo(templateId);
        assertThat(captor.getValue().getRandomSeed()).isEqualTo(42L);
        assertThat(captor.getValue().getSectionWeights()).singleElement()
                .satisfies(weight -> assertThat(weight.getWeightPercent()).isEqualTo(100));
    }
}
