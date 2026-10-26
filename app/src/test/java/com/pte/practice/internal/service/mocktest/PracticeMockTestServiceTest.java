package com.pte.practice.internal.service.mocktest;

import com.pte.practice.internal.service.selection.PracticeQuestionPicker;
import com.pte.practice.internal.service.timing.PracticeTimingService;

import com.pte.itembank.PracticeQuestionService;
import com.pte.practice.internal.domain.PracticeMockTest;
import com.pte.practice.internal.domain.PracticeMockTestItem;
import com.pte.practice.internal.dto.response.mocktest.PracticeMockTestResponse;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.repository.PracticeMockTestItemRepository;
import com.pte.practice.internal.repository.PracticeMockTestRepository;
import com.pte.practice.internal.repository.PracticeResponseRepository;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeMockTestServiceTest {

    @Mock
    private PracticeMockTestRepository mockTestRepository;
    @Mock
    private PracticeMockTestItemRepository mockTestItemRepository;
    @Mock
    private PracticeSessionRepository sessionRepository;
    @Mock
    private PracticeResponseRepository sessionItemRepository;
    @Mock
    private PracticeQuestionService questionService;
    @Mock
    private ExamDefinitionService scoreTemplateService;
    @Mock
    private PracticeTimingService timingService;

    private PracticeMockTestService service;

    @BeforeEach
    void setUp() {
        service = new PracticeMockTestService(mockTestRepository, mockTestItemRepository, sessionRepository,
                sessionItemRepository, questionService, new PracticeQuestionPicker(), scoreTemplateService,
                timingService, new PracticeScoreEstimator());
        // Hand the entity back as saved, with an id, like the database would.
        org.mockito.Mockito.lenient().when(mockTestRepository.saveAndFlush(any(PracticeMockTest.class))).thenAnswer(invocation -> {
            PracticeMockTest test = invocation.getArgument(0);
            test.setId((long) test.getSequenceNo());
            test.setPublicId(UUID.randomUUID());
            return test;
        });
    }

    private static ExamDefinitionItemResponse templateItem(String type, String section, int sequence, int minCount) {
        return new ExamDefinitionItemResponse(type, section, sequence, minCount, minCount, 5, 40, "AI",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private void activeTemplate(ExamDefinitionItemResponse... items) {
        when(scoreTemplateService.findActive()).thenReturn(Optional.of(
                new ExamDefinitionResponse(UUID.randomUUID(), "T", 3, "T", "ACTIVE", List.of(items))));
    }

    private static List<UUID> ids(int count) {
        return IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
    }

    @SuppressWarnings("unchecked")
    private List<PracticeMockTestItem> savedItems() {
        ArgumentCaptor<List<PracticeMockTestItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(mockTestItemRepository, org.mockito.Mockito.atLeastOnce()).saveAll(captor.capture());
        return captor.getAllValues().stream().flatMap(List::stream).toList();
    }

    @Test
    void generatePinsMinCountQuestionsPerTypeInSectionOrder() {
        activeTemplate(templateItem("MC_READING_SINGLE", "READING", 1, 2), templateItem("READ_ALOUD", "SPEAKING", 1, 3));
        when(questionService.findPublishedCandidateIds("READ_ALOUD")).thenReturn(ids(5));
        when(questionService.findPublishedCandidateIds("MC_READING_SINGLE")).thenReturn(ids(4));
        when(mockTestRepository.findMaxSequenceNo()).thenReturn(0);
        when(mockTestItemRepository.findAll()).thenReturn(List.of());
        when(timingService.activeTimings()).thenReturn(Map.of());

        List<PracticeMockTestResponse> created = service.generate(1);

        assertThat(created).singleElement().satisfies(test -> {
            assertThat(test.sequenceNo()).isEqualTo(1);
            assertThat(test.title()).isEqualTo("PTE Academic Full Mock Test 1");
            assertThat(test.itemCount()).isEqualTo(5);
        });
        List<PracticeMockTestItem> items = savedItems();
        assertThat(items).extracting(PracticeMockTestItem::getTaskCode)
                .containsExactly("READ_ALOUD", "READ_ALOUD", "READ_ALOUD", "MC_READING_SINGLE", "MC_READING_SINGLE");
        assertThat(items).extracting(PracticeMockTestItem::getOrderIndex).containsExactly(0, 1, 2, 3, 4);
        assertThat(items).extracting(PracticeMockTestItem::getQuestionPublicId).doesNotHaveDuplicates();
    }

    @Test
    void sameTestNumberAlwaysDrawsTheSameQuestions() {
        List<UUID> pool = ids(20);
        activeTemplate(templateItem("READ_ALOUD", "SPEAKING", 1, 4));
        when(questionService.findPublishedCandidateIds("READ_ALOUD")).thenReturn(pool);
        when(mockTestRepository.findMaxSequenceNo()).thenReturn(6);
        when(mockTestItemRepository.findAll()).thenReturn(List.of());
        when(timingService.activeTimings()).thenReturn(Map.of());

        service.generate(1);
        List<UUID> first = savedItems().stream().map(PracticeMockTestItem::getQuestionPublicId).toList();
        org.mockito.Mockito.clearInvocations(mockTestItemRepository);
        service.generate(1);
        List<UUID> second = savedItems().stream().map(PracticeMockTestItem::getQuestionPublicId).toList();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void laterTestsPreferQuestionsThatNoTestUsesYet() {
        List<UUID> pool = ids(6);
        activeTemplate(templateItem("READ_ALOUD", "SPEAKING", 1, 3));
        when(questionService.findPublishedCandidateIds("READ_ALOUD")).thenReturn(pool);
        when(mockTestRepository.findMaxSequenceNo()).thenReturn(0);
        when(mockTestItemRepository.findAll()).thenReturn(List.of());
        when(timingService.activeTimings()).thenReturn(Map.of());

        service.generate(2);

        List<UUID> all = savedItems().stream().map(PracticeMockTestItem::getQuestionPublicId).toList();
        assertThat(all).hasSize(6).doesNotHaveDuplicates();
    }

    @Test
    void notEnoughQuestionsCreatesNothingAndNamesTheShortTypes() {
        activeTemplate(templateItem("READ_ALOUD", "SPEAKING", 1, 3), templateItem("MC_READING_SINGLE", "READING", 1, 2));
        when(questionService.findPublishedCandidateIds("READ_ALOUD")).thenReturn(ids(3));
        when(questionService.findPublishedCandidateIds("MC_READING_SINGLE")).thenReturn(ids(1));

        assertThatThrownBy(() -> service.generate(2))
                .isInstanceOf(PracticeSessionException.class)
                .hasFieldOrPropertyWithValue("code", "PRACTICE_MOCK_TEST_BANK_INSUFFICIENT")
                .hasFieldOrPropertyWithValue("userMessage", "Not enough practice questions for: MC_READING_SINGLE (1/2)");
        verify(mockTestRepository, never()).saveAndFlush(any());
        verify(mockTestItemRepository, never()).saveAll(any());
    }

    @Test
    void noActiveTemplateMeansNothingToBuildFrom() {
        when(scoreTemplateService.findActive()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(1))
                .isInstanceOf(PracticeSessionException.class)
                .hasFieldOrPropertyWithValue("code", "PRACTICE_MOCK_TEST_TEMPLATE_MISSING");
        verify(questionService, never()).findPublishedCandidateIds(anyString());
    }

    @Test
    void estimatedMinutesRoundUpToFiveAndNeverBelowFive() {
        activeTemplate(templateItem("READ_ALOUD", "SPEAKING", 1, 2));
        when(questionService.findPublishedCandidateIds("READ_ALOUD")).thenReturn(ids(2));
        when(mockTestRepository.findMaxSequenceNo()).thenReturn(0);
        when(mockTestItemRepository.findAll()).thenReturn(new ArrayList<>());
        // 2 questions x (3 + 40 + 40) = 166 s = 3 min -> rounds up to 5
        when(timingService.activeTimings()).thenReturn(Map.of(
                PracticeTimingService.normalize("READ_ALOUD"), new PracticeTimingService.Timing(3, 40, 40)));

        assertThat(service.generate(1).get(0).estimatedMinutes()).isEqualTo(5);
    }

    @Test
    void capacityIsHowManyTestsTheBankFillsWithoutRepeatingAQuestion() {
        List<ExamDefinitionItemResponse> plan = List.of(templateItem("READ_ALOUD", "SPEAKING", 1, 6),
                templateItem("REPEAT_SENTENCE", "SPEAKING", 2, 10));
        when(questionService.countPublishedByTaskTypeKeys(any())).thenReturn(Map.of(
                "READ_ALOUD", 24L, "REPEAT_SENTENCE", 25L));
        assertThat(service.capacity(plan)).isEqualTo(2); // 24/6 = 4, 25/10 = 2

        when(questionService.countPublishedByTaskTypeKeys(any())).thenReturn(Map.of("READ_ALOUD", 24L));
        assertThat(service.capacity(plan)).isZero();
    }

    @Test
    void ensureCapacityBuildsOnlyTheMissingTests() {
        activeTemplate(templateItem("READ_ALOUD", "SPEAKING", 1, 2));
        when(questionService.countPublishedByTaskTypeKeys(any())).thenReturn(Map.of("READ_ALOUD", 6L));
        when(mockTestRepository.countByDeletedFalse()).thenReturn(1L);
        when(questionService.findPublishedCandidateIds("READ_ALOUD")).thenReturn(ids(6));
        when(mockTestRepository.findMaxSequenceNo()).thenReturn(1);
        when(mockTestItemRepository.findAll()).thenReturn(List.of());
        when(timingService.activeTimings()).thenReturn(Map.of());

        service.ensureCapacity();

        // capacity 3, one exists -> two new tests of two questions each
        assertThat(savedItems()).hasSize(4);
    }

    @Test
    void ensureCapacityDoesNothingWhenTheBankIsTooSmallOrThereIsNoTemplate() {
        when(scoreTemplateService.findActive()).thenReturn(Optional.empty());
        service.ensureCapacity();

        activeTemplate(templateItem("READ_ALOUD", "SPEAKING", 1, 2));
        when(questionService.countPublishedByTaskTypeKeys(any())).thenReturn(Map.of("READ_ALOUD", 1L));
        service.ensureCapacity();

        verify(mockTestRepository, never()).saveAndFlush(any());
    }
}
