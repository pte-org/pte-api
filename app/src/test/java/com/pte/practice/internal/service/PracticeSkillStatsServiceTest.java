package com.pte.practice.internal.service;

import com.pte.itembank.PracticeQuestionService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeSkillStatsServiceTest {

    @Mock
    private PracticeQuestionService questionService;
    @Mock
    private PracticeSessionItemRepository itemRepository;

    private static PracticeCatalogTaskResponse task(String code) {
        PracticeCatalogTaskResponse task = mock(PracticeCatalogTaskResponse.class);
        when(task.code()).thenReturn(code);
        return task;
    }

    @Test
    void sumsAvailableAndAnsweredQuestionsOverTheTaskTypesOfEachSkill() {
        UUID student = UUID.randomUUID();
        var catalog = new PracticeCatalogResponse("P", "T", "v", 0, List.of(
                new PracticeCatalogSectionResponse("READING", "Reading",
                        List.of(task("MC_READING_SINGLE"), task("RE_ORDER_PARAGRAPHS"))),
                new PracticeCatalogSectionResponse("SPEAKING", "Speaking", List.of(task("READ_ALOUD")))));
        when(questionService.countPublishedByTaskTypeKeys(any())).thenReturn(Map.of(
                "MC_READING_SINGLE", 12L, "RE_ORDER_PARAGRAPHS", 5L));
        when(itemRepository.countAnsweredSourceQuestionsByTaskCode(student)).thenReturn(List.of(
                new Object[] {"MC_READING_SINGLE", 3L}, new Object[] {"READ_ALOUD", 1L}));

        var result = new PracticeSkillStatsService(questionService, itemRepository).withCounts(catalog, student);

        assertThat(result.sections().get(0).practiceQuestionCount()).isEqualTo(17);
        assertThat(result.sections().get(0).answeredQuestionCount()).isEqualTo(3);
        assertThat(result.sections().get(1).practiceQuestionCount()).isZero();
        assertThat(result.sections().get(1).answeredQuestionCount()).isEqualTo(1);
    }
}
