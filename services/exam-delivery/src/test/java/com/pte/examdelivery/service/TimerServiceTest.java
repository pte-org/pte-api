package com.pte.examdelivery.service;

import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.TimerState;
import com.pte.examdelivery.domain.enums.TimerPhase;
import com.pte.examdelivery.repository.TimerStateRepository;
import com.pte.examdelivery.service.cache.PinnedItemView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Covers the section-scoped vs task-scoped timer split: READING shares one
 * deadline across every task in the section (summed from all its items),
 * everything else keeps the original per-task deadline.
 */
@ExtendWith(MockitoExtension.class)
class TimerServiceTest {

    @Mock
    private TimerStateRepository timerStateRepository;

    private TimerService timerService;
    private ExamAttempt attempt;

    @BeforeEach
    void setUp() {
        timerService = new TimerService(timerStateRepository);
        attempt = new ExamAttempt();
        when(timerStateRepository.save(any(TimerState.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private PinnedItemView readingItem(int orderIndex, int responseSeconds) {
        return new PinnedItemView(UUID.randomUUID(), orderIndex, "READING", "MC_READING_SINGLE", "title", "prompt",
                null, null, null, null, null, null, "[]", 0, responseSeconds, null, null);
    }

    private PinnedItemView taskScopedItem(int orderIndex, String section, String taskType, int prepSeconds,
                                           int responseSeconds) {
        return new PinnedItemView(UUID.randomUUID(), orderIndex, section, taskType, "title", "prompt", null, null,
                null, null, null, null, "[]", prepSeconds, responseSeconds, null, null);
    }

    @Test
    void firstReadingTask_deadlineEqualsSumOfAllReadingItems() {
        List<PinnedItemView> items = List.of(readingItem(0, 60), readingItem(1, 90), readingItem(2, 75));
        when(timerStateRepository.findByAttemptId(any())).thenReturn(Optional.empty());

        Instant before = Instant.now();
        TimerState state = timerService.startTaskTimer(attempt, items.get(0), items);
        Instant after = Instant.now();

        long totalSeconds = 60 + 90 + 75;
        assertThat(state.getPhase()).isEqualTo(TimerPhase.RESPONSE);
        assertThat(state.getActiveSection()).isEqualTo("READING");
        assertThat(state.getResponseDeadline())
                .isAfterOrEqualTo(before.plusSeconds(totalSeconds))
                .isBeforeOrEqualTo(after.plusSeconds(totalSeconds));
    }

    @Test
    void secondReadingTask_reusesSameDeadlineAsFirst() {
        List<PinnedItemView> items = List.of(readingItem(0, 60), readingItem(1, 90), readingItem(2, 75));
        when(timerStateRepository.findByAttemptId(any())).thenReturn(Optional.empty());

        TimerState firstState = timerService.startTaskTimer(attempt, items.get(0), items);
        when(timerStateRepository.findByAttemptId(any())).thenReturn(Optional.of(firstState));

        TimerState secondState = timerService.startTaskTimer(attempt, items.get(1), items);

        assertThat(secondState.getResponseDeadline()).isEqualTo(firstState.getResponseDeadline());
        assertThat(secondState.getPrepDeadline()).isEqualTo(firstState.getPrepDeadline());
        assertThat(secondState.getCurrentOrderIndex()).isEqualTo(1);
        assertThat(secondState.getActiveSection()).isEqualTo("READING");
    }

    @Test
    void readAloud_taskScoped_getsFreshPerTaskDeadline_unaffectedBySectionScopedSections() {
        PinnedItemView first = taskScopedItem(0, "SPEAKING", "READ_ALOUD", 35, 40);
        PinnedItemView second = taskScopedItem(1, "SPEAKING", "READ_ALOUD", 35, 40);
        List<PinnedItemView> items = List.of(first, second);
        when(timerStateRepository.findByAttemptId(any())).thenReturn(Optional.empty());

        TimerState firstState = timerService.startTaskTimer(attempt, first, items);
        when(timerStateRepository.findByAttemptId(any())).thenReturn(Optional.of(firstState));

        Instant before = Instant.now();
        TimerState secondState = timerService.startTaskTimer(attempt, second, items);
        Instant after = Instant.now();

        // Task-scoped (not in SECTION_SCOPED_SECTIONS): each task gets its own
        // fresh prep/response deadline computed from its own start time, not the
        // previous task's deadline reused verbatim.
        assertThat(secondState.getActiveSection()).isNull();
        assertThat(secondState.getPrepDeadline()).isAfterOrEqualTo(before.plusSeconds(35)).isBeforeOrEqualTo(after.plusSeconds(35));
        assertThat(secondState.getResponseDeadline())
                .isAfterOrEqualTo(before.plusSeconds(75))
                .isBeforeOrEqualTo(after.plusSeconds(75));
    }

    @Test
    void enteringReadingAfterAnotherSection_computesFreshSectionDeadline() {
        PinnedItemView speaking = taskScopedItem(0, "SPEAKING", "READ_ALOUD", 35, 40);
        PinnedItemView firstReading = readingItem(1, 60);
        PinnedItemView secondReading = readingItem(2, 90);
        List<PinnedItemView> items = List.of(speaking, firstReading, secondReading);
        when(timerStateRepository.findByAttemptId(any())).thenReturn(Optional.empty());

        TimerState speakingState = timerService.startTaskTimer(attempt, speaking, items);
        when(timerStateRepository.findByAttemptId(any())).thenReturn(Optional.of(speakingState));

        Instant before = Instant.now();
        TimerState readingState = timerService.startTaskTimer(attempt, firstReading, items);
        Instant after = Instant.now();

        long readingTotalSeconds = 60 + 90;
        assertThat(readingState.getActiveSection()).isEqualTo("READING");
        assertThat(readingState.getResponseDeadline())
                .isAfterOrEqualTo(before.plusSeconds(readingTotalSeconds))
                .isBeforeOrEqualTo(after.plusSeconds(readingTotalSeconds));
    }

    @Test
    void save_isCalledWithPersistedState() {
        List<PinnedItemView> items = List.of(readingItem(0, 60));
        when(timerStateRepository.findByAttemptId(any())).thenReturn(Optional.empty());

        timerService.startTaskTimer(attempt, items.get(0), items);

        ArgumentCaptor<TimerState> captor = ArgumentCaptor.forClass(TimerState.class);
        org.mockito.Mockito.verify(timerStateRepository).save(captor.capture());
        assertThat(captor.getValue().getAttempt()).isSameAs(attempt);
    }
}
