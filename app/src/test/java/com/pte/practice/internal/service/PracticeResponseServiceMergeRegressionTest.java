package com.pte.practice.internal.service;

import com.pte.itembank.PracticeQuestionService;
import com.pte.itembank.dto.response.PracticeQuestionContent;
import com.pte.itembank.dto.response.PracticeQuestionEvaluationContent;
import com.pte.itembank.dto.response.PracticeQuestionPin;
import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.enums.PracticeSessionKind;
import com.pte.practice.internal.domain.enums.PracticeTimingMode;
import com.pte.practice.internal.dto.response.PracticeCatalogAvailability;
import com.pte.practice.internal.dto.response.PracticeCatalogTaskResponse;
import com.pte.practice.internal.repository.PracticeResponseRepository;
import com.pte.practice.internal.service.selection.PracticeQuestionPicker;
import com.pte.practice.internal.service.timing.PracticeTimingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeResponseServiceMergeRegressionTest {

    @Test
    void creatingTimedResponsesHonorsInjectedPickerAndPinsQuestionContentAndTemplateTiming() {
        PracticeResponseRepository repository = mock(PracticeResponseRepository.class);
        PracticeCatalogService catalog = mock(PracticeCatalogService.class);
        PracticeQuestionService questions = mock(PracticeQuestionService.class);
        PracticeQuestionPicker picker = mock(PracticeQuestionPicker.class);
        PracticeTimingService timing = mock(PracticeTimingService.class);
        PracticeSession session = new PracticeSession();
        session.setId(17L);
        session.setStudentPublicId(UUID.randomUUID());
        session.setKind(PracticeSessionKind.TASK);
        session.setProductCode("PTE_ACADEMIC");
        session.setSelectedTaskTypes("READ_ALOUD");
        session.setQuestionCount(1);
        session.setTimingMode(PracticeTimingMode.TIMED);
        UUID seen = UUID.randomUUID();
        UUID selected = UUID.randomUUID();
        PracticeCatalogTaskResponse task = new PracticeCatalogTaskResponse("READ_ALOUD", "Read Aloud",
                "SPEAKING", true, PracticeCatalogAvailability.RUNNABLE, null, null, 1,
                "READ_ALOUD_V1", 1, 1, List.of(), "READY", "test");
        Map<String, PracticeTimingService.Timing> timings = Map.of(
                PracticeTimingService.normalize(task.code()), new PracticeTimingService.Timing(3, 40, 45));
        when(catalog.resolveStartableTasks(eq(session.getProductCode()), eq(Set.of(task.code())), any()))
                .thenReturn(List.of(task));
        when(timing.activeTimings()).thenReturn(timings);
        when(timing.covers(timings, List.of(task.code()))).thenReturn(true);
        when(repository.findSeenSourceQuestionIds(session.getStudentPublicId(), List.of(task.code())))
                .thenReturn(List.of(seen));
        when(questions.findPublishedCandidateIds(task.code())).thenReturn(List.of(seen, selected));
        when(picker.pick(List.of(seen, selected), Set.of(seen), 1)).thenReturn(List.of(selected));
        PracticeQuestionContent content = new PracticeQuestionContent(selected, task.code(), "Pinned prompt",
                null, null, null, null, List.of());
        PracticeQuestionEvaluationContent evaluation = new PracticeQuestionEvaluationContent(selected,
                "Pinned reference", null, List.of(), null, null);
        when(questions.findPublishedQuestionsForPinning(List.of(selected)))
                .thenReturn(List.of(new PracticeQuestionPin(content, evaluation)));
        PracticeResponseService service = new PracticeResponseService(repository, catalog,
                mock(PracticeAnswerValidationService.class), null, questions, JsonMapper.builder().build(),
                null, null, null, picker, timing);

        service.createItemsIfNeeded(session);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<PracticeResponse>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(response -> {
            assertThat(response.getPracticeSessionId()).isEqualTo(17L);
            assertThat(response.getSourceQuestionPublicId()).isEqualTo(selected);
            assertThat(response.getPromptText()).isEqualTo("Pinned prompt");
            assertThat(response.getReferenceAnswerText()).isEqualTo("Pinned reference");
            assertThat(response.getPreListenSeconds()).isEqualTo(3);
            assertThat(response.getPreRecordSeconds()).isEqualTo(40);
            assertThat(response.getResponseSeconds()).isEqualTo(45);
            assertThat(response.getOrderIndex()).isZero();
        });
        assertThat(session.effectiveTimingMode()).isEqualTo(PracticeTimingMode.TIMED);
        verify(picker).pick(List.of(seen, selected), Set.of(seen), 1);
    }
}
