package com.pte.assessment.internal.service;

import com.pte.assessment.domain.BlueprintItem;
import com.pte.assessment.domain.ExamBlueprint;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.assessment.internal.exception.InvalidSectionException;
import com.pte.assessment.internal.exception.InvalidSkillSelectionException;
import com.pte.assessment.internal.exception.InsufficientQuestionBankException;
import com.pte.assessment.internal.repository.ExamBlueprintRepository;
import com.pte.itembank.ItembankService;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * V5-seed-shaped {@link ScoreTemplateResponse} (mirrors V14__score_template.sql
 * exactly: task type/section/sequence/min/max) so the statistical assertions
 * below are checked against the real template shape, not an arbitrary fixture.
 */
@ExtendWith(MockitoExtension.class)
class ExamGenerationServiceTest {

    @Mock
    private ScoreTemplateService scoreTemplateService;
    @Mock
    private ItembankService itembankService;
    @Mock
    private ExamBlueprintRepository blueprintRepository;
    @Mock
    private SnapshotPublishService snapshotPublishService;

    private ExamGenerationService service;
    private CurrentUser hostCaller;

    private record Row(String taskType, String section, int sequence, int min, int max) {
    }

    private static final List<Row> V5_ROWS = List.of(
            new Row("READ_ALOUD", "SPEAKING", 1, 6, 7),
            new Row("REPEAT_SENTENCE", "SPEAKING", 2, 10, 12),
            new Row("DESCRIBE_IMAGE", "SPEAKING", 3, 5, 6),
            new Row("RE_TELL_LECTURE", "SPEAKING", 4, 2, 3),
            new Row("ANSWER_SHORT_QUESTION", "SPEAKING", 5, 5, 6),
            new Row("SUMMARIZE_GROUP_DISCUSSION", "SPEAKING", 6, 2, 3),
            new Row("RESPOND_TO_A_SITUATION", "SPEAKING", 7, 2, 3),
            new Row("SUMMARIZE_WRITTEN_TEXT", "WRITING", 8, 2, 2),
            new Row("WRITE_ESSAY", "WRITING", 9, 1, 1),
            new Row("FILL_IN_THE_BLANKS_DROPDOWN", "READING", 10, 5, 6),
            new Row("MC_READING_MULTIPLE", "READING", 11, 2, 3),
            new Row("RE_ORDER_PARAGRAPHS", "READING", 12, 2, 3),
            new Row("FILL_IN_THE_BLANKS_DRAG_AND_DROP", "READING", 13, 4, 5),
            new Row("MC_READING_SINGLE", "READING", 14, 2, 3),
            new Row("SUMMARIZE_SPOKEN_TEXT", "LISTENING", 15, 1, 1),
            new Row("MC_LISTENING_MULTIPLE", "LISTENING", 16, 2, 3),
            new Row("FILL_IN_THE_BLANKS_TYPE_IN", "LISTENING", 17, 2, 3),
            new Row("HIGHLIGHT_CORRECT_SUMMARY", "LISTENING", 18, 2, 3),
            new Row("MC_LISTENING_SINGLE", "LISTENING", 19, 2, 3),
            new Row("SELECT_MISSING_WORD", "LISTENING", 20, 1, 2),
            new Row("HIGHLIGHT_INCORRECT_WORDS", "LISTENING", 21, 2, 3),
            new Row("WRITE_FROM_DICTATION", "LISTENING", 22, 3, 4));

    private ScoreTemplateResponse v5Template() {
        List<ScoreTemplateItemResponse> items = V5_ROWS.stream()
                .map(r -> new ScoreTemplateItemResponse(r.taskType(), r.section(), r.sequence(), r.min(), r.max(),
                        0, 30, "OBJECTIVE", BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO))
                .toList();
        return new ScoreTemplateResponse(UUID.randomUUID(), "PTE_Score_Template", 1, "APEUni PTE Score Table V5",
                "ACTIVE", items);
    }

    @BeforeEach
    void setUp() {
        service = new ExamGenerationService(scoreTemplateService, itembankService, blueprintRepository,
                snapshotPublishService);
        hostCaller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));
    }

    /** Every requested task type has "infinite" stock: count = maxCount, random draw returns exactly n ids. */
    private void stubUnlimitedStock() {
        when(scoreTemplateService.getActive()).thenReturn(v5Template());
        when(itembankService.countPublishedByTaskTypes(anySet())).thenAnswer(inv -> {
            Set<PteTaskType> requested = inv.getArgument(0);
            Map<PteTaskType, Long> counts = new EnumMap<>(PteTaskType.class);
            requested.forEach(t -> counts.put(t, 100L));
            return counts;
        });
        when(itembankService.randomPublishedQuestionIds(any(PteTaskType.class), anyInt())).thenAnswer(inv -> {
            int n = inv.getArgument(1);
            List<UUID> ids = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                ids.add(UUID.randomUUID());
            }
            return ids;
        });
        when(blueprintRepository.save(any(ExamBlueprint.class))).thenAnswer(inv -> {
            ExamBlueprint blueprint = inv.getArgument(0);
            blueprint.setPublicId(UUID.randomUUID());
            return blueprint;
        });
        when(snapshotPublishService.publish(any(UUID.class), any(CurrentUser.class)))
                .thenReturn(mock(SnapshotResponse.class));
    }

    @Test
    void generate_fourSkills_100Runs_everyTaskTypeCountWithinTemplateRange() {
        stubUnlimitedStock();
        Set<String> allSkills = Set.of("SPEAKING", "WRITING", "READING", "LISTENING");
        Map<String, Row> byTaskType = new java.util.HashMap<>();
        V5_ROWS.forEach(r -> byTaskType.put(r.taskType(), r));
        int minTotal = V5_ROWS.stream().mapToInt(Row::min).sum() + 1; // +1 for PERSONAL_INTRODUCTION
        int maxTotal = V5_ROWS.stream().mapToInt(Row::max).sum() + 1;

        for (int run = 0; run < 100; run++) {
            SnapshotResponse response = service.generate("Exam " + run, allSkills, hostCaller);
            assertThat(response).isNotNull();
        }

        org.mockito.ArgumentCaptor<ExamBlueprint> blueprintCaptor = org.mockito.ArgumentCaptor.forClass(ExamBlueprint.class);
        verify(blueprintRepository, org.mockito.Mockito.times(100)).save(blueprintCaptor.capture());
        for (ExamBlueprint blueprint : blueprintCaptor.getAllValues()) {
            assertThat(blueprint.getItems().size()).isBetween(minTotal, maxTotal);
        }

        // Every per-task-type draw requested a count within that task type's [min, max] (PI is fixed at 1).
        org.mockito.ArgumentCaptor<PteTaskType> taskTypeCaptor = org.mockito.ArgumentCaptor.forClass(PteTaskType.class);
        org.mockito.ArgumentCaptor<Integer> countCaptor = org.mockito.ArgumentCaptor.forClass(Integer.class);
        verify(itembankService, org.mockito.Mockito.times(100 * (V5_ROWS.size() + 1)))
                .randomPublishedQuestionIds(taskTypeCaptor.capture(), countCaptor.capture());
        List<PteTaskType> taskTypes = taskTypeCaptor.getAllValues();
        List<Integer> counts = countCaptor.getAllValues();
        for (int i = 0; i < taskTypes.size(); i++) {
            PteTaskType taskType = taskTypes.get(i);
            int n = counts.get(i);
            if (taskType == PteTaskType.PERSONAL_INTRODUCTION) {
                assertThat(n).isEqualTo(1);
                continue;
            }
            Row row = byTaskType.get(taskType.name());
            assertThat(n).as("draw count for %s", taskType).isBetween(row.min(), row.max());
        }
    }

    @Test
    void generate_fourSkills_includesPersonalIntroductionOnceFirstInSpeaking() {
        stubUnlimitedStock();
        Set<String> allSkills = Set.of("SPEAKING", "WRITING", "READING", "LISTENING");

        service.generate("Exam", allSkills, hostCaller);

        org.mockito.ArgumentCaptor<ExamBlueprint> captor = org.mockito.ArgumentCaptor.forClass(ExamBlueprint.class);
        verify(blueprintRepository).save(captor.capture());
        ExamBlueprint blueprint = captor.getValue();
        assertThat(blueprint.getItems().get(0).getSection().name()).isEqualTo("SPEAKING");
        // PI is the only PERSONAL_INTRODUCTION draw; itembank is asked for it exactly once with n=1.
        verify(itembankService).randomPublishedQuestionIds(PteTaskType.PERSONAL_INTRODUCTION, 1);
    }

    @Test
    void generate_orderIsSpeakingWritingReadingListening_thenBySequence() {
        stubUnlimitedStock();
        Set<String> allSkills = Set.of("SPEAKING", "WRITING", "READING", "LISTENING");

        service.generate("Exam", allSkills, hostCaller);

        org.mockito.ArgumentCaptor<ExamBlueprint> captor = org.mockito.ArgumentCaptor.forClass(ExamBlueprint.class);
        verify(blueprintRepository).save(captor.capture());
        List<String> sections = captor.getValue().getItems().stream().map(i -> i.getSection().name()).toList();
        List<String> order = List.of("SPEAKING", "WRITING", "READING", "LISTENING");
        int lastRank = -1;
        for (String section : sections) {
            int rank = order.indexOf(section);
            assertThat(rank).isGreaterThanOrEqualTo(lastRank);
            lastRank = rank;
        }
        // orderIndex is sequential starting at 0.
        for (int i = 0; i < captor.getValue().getItems().size(); i++) {
            assertThat(captor.getValue().getItems().get(i).getOrderIndex()).isEqualTo(i);
        }
    }

    @Test
    void generate_oneToThreeSkills_zeroItemsOutsideSelectedSections() {
        stubUnlimitedStock();
        List<String> all = List.of("SPEAKING", "WRITING", "READING", "LISTENING");
        List<Set<String>> subsets = new ArrayList<>();
        for (int mask = 1; mask < 16; mask++) {
            int size = Integer.bitCount(mask);
            if (size < 1 || size > 3) {
                continue;
            }
            Set<String> subset = new HashSet<>();
            for (int bit = 0; bit < 4; bit++) {
                if ((mask & (1 << bit)) != 0) {
                    subset.add(all.get(bit));
                }
            }
            subsets.add(subset);
        }

        for (Set<String> subset : subsets) {
            org.mockito.Mockito.clearInvocations(blueprintRepository);
            service.generate("Exam", subset, hostCaller);
            org.mockito.ArgumentCaptor<ExamBlueprint> captor = org.mockito.ArgumentCaptor.forClass(ExamBlueprint.class);
            verify(blueprintRepository).save(captor.capture());
            for (BlueprintItem item : captor.getValue().getItems()) {
                assertThat(subset).contains(item.getSection().name());
            }
        }
    }

    @Test
    void generate_randomSelectionReturnsFewerThanN_throwsInsufficient_savesNothing() {
        when(scoreTemplateService.getActive()).thenReturn(v5Template());
        when(itembankService.countPublishedByTaskTypes(anySet())).thenAnswer(inv -> {
            Set<PteTaskType> requested = inv.getArgument(0);
            Map<PteTaskType, Long> counts = new EnumMap<>(PteTaskType.class);
            requested.forEach(t -> counts.put(t, 100L));
            return counts;
        });
        // Every draw succeeds except READ_ALOUD, which returns fewer than requested (archived mid-flight).
        when(itembankService.randomPublishedQuestionIds(any(PteTaskType.class), anyInt())).thenAnswer(inv -> {
            PteTaskType taskType = inv.getArgument(0);
            int n = inv.getArgument(1);
            if (taskType == PteTaskType.READ_ALOUD) {
                return List.of(UUID.randomUUID());
            }
            List<UUID> ids = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                ids.add(UUID.randomUUID());
            }
            return ids;
        });

        assertThatThrownBy(() -> service.generate("Exam", Set.of("SPEAKING"), hostCaller))
                .isInstanceOf(InsufficientQuestionBankException.class);

        verify(blueprintRepository, never()).save(any());
        verify(snapshotPublishService, never()).publish(any(), any());
    }

    @Test
    void generate_oneShortageAmongMany_throwsWithAllShortagesListed_savesNothing() {
        when(scoreTemplateService.getActive()).thenReturn(v5Template());
        when(itembankService.countPublishedByTaskTypes(anySet())).thenAnswer(inv -> {
            Set<PteTaskType> requested = inv.getArgument(0);
            Map<PteTaskType, Long> counts = new EnumMap<>(PteTaskType.class);
            requested.forEach(t -> counts.put(t, 100L));
            // 3 task types are short of stock.
            counts.put(PteTaskType.READ_ALOUD, 1L);
            counts.put(PteTaskType.WRITE_ESSAY, 0L);
            counts.put(PteTaskType.WRITE_FROM_DICTATION, 1L);
            return counts;
        });

        InsufficientQuestionBankException ex = org.junit.jupiter.api.Assertions.assertThrows(
                InsufficientQuestionBankException.class,
                () -> service.generate("Exam", Set.of("SPEAKING", "WRITING", "LISTENING"), hostCaller));

        assertThat(ex.getShortages()).extracting(InsufficientQuestionBankException.Shortage::taskType)
                .containsExactlyInAnyOrder("READ_ALOUD", "WRITE_ESSAY", "WRITE_FROM_DICTATION");
        verifyNoInteractions(blueprintRepository);
        verifyNoInteractions(snapshotPublishService);
        verify(itembankService, never()).randomPublishedQuestionIds(any(), anyInt());
    }

    @Test
    void generate_missingPersonalIntroductionStock_reportedAsShortage() {
        when(scoreTemplateService.getActive()).thenReturn(v5Template());
        when(itembankService.countPublishedByTaskTypes(anySet())).thenAnswer(inv -> {
            Set<PteTaskType> requested = inv.getArgument(0);
            Map<PteTaskType, Long> counts = new EnumMap<>(PteTaskType.class);
            requested.forEach(t -> counts.put(t, 100L));
            counts.put(PteTaskType.PERSONAL_INTRODUCTION, 0L);
            return counts;
        });

        InsufficientQuestionBankException ex = org.junit.jupiter.api.Assertions.assertThrows(
                InsufficientQuestionBankException.class,
                () -> service.generate("Exam", Set.of("SPEAKING"), hostCaller));

        assertThat(ex.getShortages()).extracting(InsufficientQuestionBankException.Shortage::taskType)
                .contains("PERSONAL_INTRODUCTION");
    }

    @Test
    void generate_emptyOrInvalidSkills_rejectedBeforeAnyQuery() {
        assertThatThrownBy(() -> service.generate("Exam", Set.of(), hostCaller))
                .isInstanceOf(InvalidSkillSelectionException.class);
        assertThatThrownBy(() -> service.generate("Exam",
                Set.of("SPEAKING", "WRITING", "READING", "LISTENING", "BOGUS"), hostCaller))
                .isInstanceOf(InvalidSkillSelectionException.class);
        assertThatThrownBy(() -> service.generate("Exam", Set.of("NOT_A_SECTION"), hostCaller))
                .isInstanceOf(InvalidSectionException.class);

        verifyNoInteractions(scoreTemplateService);
        verifyNoInteractions(itembankService);
    }

    @Test
    void generate_neverPassesCallerToItembankFacade() {
        stubUnlimitedStock();

        service.generate("Exam", Set.of("SPEAKING"), hostCaller);

        verify(itembankService).countPublishedByTaskTypes(anySet());
        verify(itembankService, org.mockito.Mockito.atLeastOnce())
                .randomPublishedQuestionIds(any(PteTaskType.class), anyInt());
        // No overload taking CurrentUser exists on these two methods (compile-level guard,
        // enforced already in ItembankServiceTest.generationFacades_neverTakeACurrentUserParameter).
    }

    @Test
    void generateDeterministic_sameSeedAndTemplateProducesSameBlueprintOrder() {
        UUID templateId = UUID.randomUUID();
        ScoreTemplateResponse template = new ScoreTemplateResponse(
                templateId,
                "CUSTOM",
                4,
                "Deterministic template",
                "ACTIVE",
                null,
                List.of(new ScoreTemplateItemResponse(
                        "READ_ALOUD", "SPEAKING", 1, 2, 2, 0, 30, "AI_SPEECH", BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)),
                "CUSTOM");
        when(scoreTemplateService.findActiveByPublicId(templateId)).thenReturn(Optional.of(template));
        when(itembankService.countPublishedByTaskTypes(anySet())).thenAnswer(invocation -> {
            Set<PteTaskType> requested = invocation.getArgument(0);
            Map<PteTaskType, Long> counts = new EnumMap<>(PteTaskType.class);
            requested.forEach(taskType -> counts.put(taskType, 20L));
            return counts;
        });
        when(itembankService.publishedQuestionIds(any(PteTaskType.class))).thenAnswer(invocation -> {
            PteTaskType taskType = invocation.getArgument(0);
            return java.util.stream.IntStream.range(0, 20)
                    .mapToObj(index -> UUID.nameUUIDFromBytes((taskType.name() + index).getBytes()))
                    .toList();
        });
        when(blueprintRepository.save(any(ExamBlueprint.class))).thenAnswer(invocation -> {
            ExamBlueprint blueprint = invocation.getArgument(0);
            blueprint.setPublicId(UUID.randomUUID());
            return blueprint;
        });
        when(snapshotPublishService.publish(any(UUID.class), any(CurrentUser.class), any(ScoreTemplateResponse.class),
                anyLong(), anyString(), anyString())).thenReturn(mock(SnapshotResponse.class));

        long seed = 20260921L;
        service.generateDeterministic("Exam", templateId, seed, hostCaller);
        service.generateDeterministic("Exam", templateId, seed, hostCaller);

        org.mockito.ArgumentCaptor<ExamBlueprint> captor = org.mockito.ArgumentCaptor.forClass(ExamBlueprint.class);
        verify(blueprintRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<UUID> first = captor.getAllValues().get(0).getItems().stream()
                .map(BlueprintItem::getQuestionPublicId).toList();
        List<UUID> second = captor.getAllValues().get(1).getItems().stream()
                .map(BlueprintItem::getQuestionPublicId).toList();
        assertThat(first).containsExactlyElementsOf(second);
        verify(snapshotPublishService, org.mockito.Mockito.times(2)).publish(any(UUID.class), any(CurrentUser.class),
                eq(template), eq(seed), eq("PTE_SEEDED_V1"), eq(templateId + ":4"));
        verify(itembankService, org.mockito.Mockito.never())
                .publishedQuestionIds(PteTaskType.PERSONAL_INTRODUCTION);
    }
}
