package com.pte.practice.internal.service;

import com.pte.itembank.PracticeQuestionService;
import com.pte.practice.internal.dto.response.PracticeCatalogAvailability;
import com.pte.practice.internal.dto.response.PracticeCatalogResponse;
import com.pte.practice.internal.dto.response.PracticeCatalogSectionResponse;
import com.pte.practice.internal.dto.response.PracticeCatalogTaskResponse;
import com.pte.practice.internal.repository.PracticeSessionItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeSkillStatsServiceTest {

    @Mock
    private PracticeQuestionService questionService;
    @Mock
    private PracticeSessionItemRepository itemRepository;

    private static PracticeCatalogTaskResponse task(String code, String section) {
        return new PracticeCatalogTaskResponse(code, code, section, true, PracticeCatalogAvailability.RUNNABLE, null,
                null, null, null, null, null, List.of(), null, null);
    }

    private final UUID student = UUID.randomUUID();

    private PracticeCatalogResponse catalog() {
        return new PracticeCatalogResponse("P", "T", "v", 0, List.of(
                new PracticeCatalogSectionResponse("READING", "Reading",
                        List.of(task("MC_READING_SINGLE", "READING"), task("RE_ORDER_PARAGRAPHS", "READING"))),
                new PracticeCatalogSectionResponse("SPEAKING", "Speaking",
                        List.of(task("READ_ALOUD", "SPEAKING"), task("REPEAT_SENTENCE", "SPEAKING")))));
    }

    @Test
    void sumsAvailableAndAnsweredQuestionsOverTheTaskTypesOfEachSkill() {
        when(questionService.countPublishedByTaskTypeKeys(any())).thenReturn(Map.of(
                "MC_READING_SINGLE", 12L, "RE_ORDER_PARAGRAPHS", 5L));
        when(itemRepository.countAnsweredSourceQuestionsByTaskCode(student)).thenReturn(List.of(
                new Object[] {"MC_READING_SINGLE", 3L}, new Object[] {"READ_ALOUD", 1L}));
        when(itemRepository.averageScoreByTaskCode(student)).thenReturn(List.of());

        var result = new PracticeSkillStatsService(questionService, itemRepository).withCounts(catalog(), student);

        assertThat(result.sections().get(0).practiceQuestionCount()).isEqualTo(17);
        assertThat(result.sections().get(0).answeredQuestionCount()).isEqualTo(3);
        assertThat(result.sections().get(1).practiceQuestionCount()).isZero();
        assertThat(result.sections().get(1).answeredQuestionCount()).isEqualTo(1);
    }

    @Test
    void fillsAnsweredAndAverageScorePerTaskType() {
        when(questionService.countPublishedByTaskTypeKeys(any())).thenReturn(Map.of());
        when(itemRepository.countAnsweredSourceQuestionsByTaskCode(student)).thenReturn(List.<Object[]>of(
                new Object[] {"READ_ALOUD", 3L}));
        when(itemRepository.averageScoreByTaskCode(student)).thenReturn(List.<Object[]>of(
                new Object[] {"READ_ALOUD", 71.6d}));

        var speaking = new PracticeSkillStatsService(questionService, itemRepository)
                .withCounts(catalog(), student).sections().get(1).taskTypes();

        assertThat(speaking.get(0).answeredQuestionCount()).isEqualTo(3);
        assertThat(speaking.get(0).averageScore()).isEqualTo(72);
        // No answer yet: no count and no average, so the UI shows "—" rather than a red 0%.
        assertThat(speaking.get(1).answeredQuestionCount()).isZero();
        assertThat(speaking.get(1).averageScore()).isNull();
    }
}
