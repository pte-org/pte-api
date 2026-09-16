package com.pte.assessment.internal.service;

import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.dto.response.TemplateSpec;
import com.pte.assessment.internal.exception.InsufficientQuestionsException;
import com.pte.assessment.internal.repository.ExamBlueprintRepository;
import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.dto.response.QuestionFreezeView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateResolverServiceTest {

    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    @Mock
    private TemplateService templateService;
    @Mock
    private ItembankService itembankService;
    @Mock
    private ExamBlueprintRepository blueprintRepository;
    @Mock
    private SnapshotPublishService snapshotPublishService;

    private TemplateResolverService service;

    @BeforeEach
    void setUp() {
        service = new TemplateResolverService(templateService, itembankService, blueprintRepository,
                snapshotPublishService);
    }

    @Test
    void resolve_shortBankReportsMissingSlotAndWritesNothing() {
        TemplateSpec spec = spec(new TemplateSpec.Slot(PteTaskType.READ_ALOUD, 5, 0));
        when(templateService.getTemplateSpec(TEMPLATE_ID)).thenReturn(spec);
        when(itembankService.countAvailableByTaskType(PteTaskType.READ_ALOUD)).thenReturn(3L);

        assertThatThrownBy(() -> service.resolve(TEMPLATE_ID, 7L))
                .isInstanceOfSatisfying(InsufficientQuestionsException.class, ex -> {
                    assertThat(ex.getMissingSlots()).containsExactly(
                            new InsufficientQuestionsException.MissingQuestionSlot("READ_ALOUD", 5, 3));
                    assertThat(ex.getStatus().value()).isEqualTo(422);
                });

        verify(blueprintRepository, never()).save(any());
        verify(snapshotPublishService, never()).publishGenerated(any(), eq(7L), any(), any(), any());
    }

    @Test
    void resolve_reportsAllShortSlotsBeforeWriting() {
        TemplateSpec spec = new TemplateSpec(TEMPLATE_ID, "Template", List.of(
                new TemplateSpec.Section(PteSection.SPEAKING, 50, 0,
                        List.of(new TemplateSpec.Slot(PteTaskType.READ_ALOUD, 5, 0))),
                new TemplateSpec.Section(PteSection.WRITING, 50, 1,
                        List.of(new TemplateSpec.Slot(PteTaskType.WRITE_ESSAY, 2, 0)))));
        when(templateService.getTemplateSpec(TEMPLATE_ID)).thenReturn(spec);
        when(itembankService.countAvailableByTaskType(PteTaskType.READ_ALOUD)).thenReturn(3L);
        when(itembankService.countAvailableByTaskType(PteTaskType.WRITE_ESSAY)).thenReturn(1L);

        assertThatThrownBy(() -> service.resolve(TEMPLATE_ID, 7L))
                .isInstanceOfSatisfying(InsufficientQuestionsException.class, ex ->
                        assertThat(ex.getMissingSlots()).containsExactly(
                                new InsufficientQuestionsException.MissingQuestionSlot("READ_ALOUD", 5, 3),
                                new InsufficientQuestionsException.MissingQuestionSlot("WRITE_ESSAY", 2, 1)));
        verify(blueprintRepository, never()).save(any());
    }

    @Test
    void resolve_duplicateTaskTypeSlotsReserveDistinctQuestions() {
        TemplateSpec spec = new TemplateSpec(TEMPLATE_ID, "Template", List.of(
                new TemplateSpec.Section(PteSection.SPEAKING, 100, 0, List.of(
                        new TemplateSpec.Slot(PteTaskType.READ_ALOUD, 2, 0),
                        new TemplateSpec.Slot(PteTaskType.READ_ALOUD, 2, 1)))));
        when(templateService.getTemplateSpec(TEMPLATE_ID)).thenReturn(spec);
        when(itembankService.countAvailableByTaskType(PteTaskType.READ_ALOUD)).thenReturn(2L);

        assertThatThrownBy(() -> service.resolve(TEMPLATE_ID, 7L))
                .isInstanceOfSatisfying(InsufficientQuestionsException.class, ex ->
                        assertThat(ex.getMissingSlots()).containsExactly(
                                new InsufficientQuestionsException.MissingQuestionSlot("READ_ALOUD", 2, 0)));
        verify(itembankService, never()).findRandomByTaskType(any(), org.mockito.ArgumentMatchers.anyInt(), anyLong());
        verify(blueprintRepository, never()).save(any());
    }

    @Test
    void resolve_sameSeedPassesSameOrderedQuestionsToSnapshotPublisher() {
        TemplateSpec spec = spec(new TemplateSpec.Slot(PteTaskType.READ_ALOUD, 2, 0));
        QuestionFreezeView first = question(UUID.randomUUID(), "first");
        QuestionFreezeView second = question(UUID.randomUUID(), "second");
        when(templateService.getTemplateSpec(TEMPLATE_ID)).thenReturn(spec);
        when(itembankService.countAvailableByTaskType(PteTaskType.READ_ALOUD)).thenReturn(2L);
        when(itembankService.findRandomByTaskType(PteTaskType.READ_ALOUD, 2, 11L))
                .thenReturn(List.of(first, second));
        when(blueprintRepository.save(any(ExamBlueprint.class))).thenAnswer(invocation -> {
            ExamBlueprint blueprint = invocation.getArgument(0);
            blueprint.setPublicId(UUID.randomUUID());
            return blueprint;
        });
        when(snapshotPublishService.publishGenerated(any(), eq(11L), any(), any(), any()))
                .thenReturn(snapshotResponse());

        service.resolve(TEMPLATE_ID, 11L);
        service.resolve(TEMPLATE_ID, 11L);

        ArgumentCaptor<List<SnapshotPublishService.GeneratedItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(snapshotPublishService, org.mockito.Mockito.times(2))
                .publishGenerated(eq(TEMPLATE_ID), eq(11L), any(), eq(spec), captor.capture());
        assertThat(captor.getAllValues().get(0).stream().map(item -> item.question().sourceQuestionPublicId()))
                .containsExactly(first.sourceQuestionPublicId(), second.sourceQuestionPublicId());
        assertThat(captor.getAllValues().get(0).stream().map(item -> item.question().sourceQuestionPublicId()))
                .containsExactlyElementsOf(captor.getAllValues().get(1).stream()
                        .map(item -> item.question().sourceQuestionPublicId()).toList());
    }

    @Test
    void resolve_differentSeedsPassDifferentOrderedQuestionsToSnapshotPublisher() {
        TemplateSpec spec = spec(new TemplateSpec.Slot(PteTaskType.READ_ALOUD, 2, 0));
        QuestionFreezeView first = question(UUID.randomUUID(), "first");
        QuestionFreezeView second = question(UUID.randomUUID(), "second");
        when(templateService.getTemplateSpec(TEMPLATE_ID)).thenReturn(spec);
        when(itembankService.countAvailableByTaskType(PteTaskType.READ_ALOUD)).thenReturn(2L);
        when(itembankService.findRandomByTaskType(PteTaskType.READ_ALOUD, 2, 11L))
                .thenReturn(List.of(first, second));
        when(itembankService.findRandomByTaskType(PteTaskType.READ_ALOUD, 2, 12L))
                .thenReturn(List.of(second, first));
        when(blueprintRepository.save(any(ExamBlueprint.class))).thenAnswer(invocation -> {
            ExamBlueprint blueprint = invocation.getArgument(0);
            blueprint.setPublicId(UUID.randomUUID());
            return blueprint;
        });
        when(snapshotPublishService.publishGenerated(any(), anyLong(), any(), any(), any()))
                .thenReturn(snapshotResponse());

        service.resolve(TEMPLATE_ID, 11L);
        service.resolve(TEMPLATE_ID, 12L);

        ArgumentCaptor<List<SnapshotPublishService.GeneratedItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(snapshotPublishService, org.mockito.Mockito.times(2))
                .publishGenerated(eq(TEMPLATE_ID), anyLong(), any(), eq(spec), captor.capture());
        List<UUID> firstOrder = captor.getAllValues().get(0).stream()
                .map(item -> item.question().sourceQuestionPublicId()).toList();
        List<UUID> secondOrder = captor.getAllValues().get(1).stream()
                .map(item -> item.question().sourceQuestionPublicId()).toList();
        assertThat(firstOrder).isNotEqualTo(secondOrder);
    }

    private TemplateSpec spec(TemplateSpec.Slot slot) {
        return new TemplateSpec(TEMPLATE_ID, "Template", List.of(
                new TemplateSpec.Section(PteSection.SPEAKING, 100, 0, List.of(slot))));
    }

    private QuestionFreezeView question(UUID id, String title) {
        return new QuestionFreezeView(id, PteTaskType.READ_ALOUD, title, "prompt",
                null, null, null, null, null, null, List.of());
    }

    private SnapshotResponse snapshotResponse() {
        return new SnapshotResponse(UUID.randomUUID(), "Template", 1, UUID.randomUUID(), null, List.of());
    }
}
