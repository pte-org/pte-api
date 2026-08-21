package com.pte.examdelivery.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.TimerState;
import com.pte.examdelivery.domain.enums.AttemptStatus;
import com.pte.examdelivery.domain.enums.TimerPhase;
import com.pte.examdelivery.dto.response.AttemptTaskResponse;
import com.pte.examdelivery.dto.response.BlankGroupView;
import com.pte.examdelivery.dto.response.OptionView;
import com.pte.examdelivery.dto.response.TaskView;
import com.pte.examdelivery.service.cache.PinnedItemView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers Phase 1's "Backend Data Contracts & DTOs" mapper changes: the
 * {@code options}/{@code blankGroups} branching in {@code toTaskResponse}, the
 * {@code orderIndex} int-to-String change, and the mixed-blankIndex guard.
 */
class AttemptMapperTest {

    private final AttemptMapper mapper = new AttemptMapper(new ObjectMapper());
    private final UUID pinnedItemPublicId = UUID.randomUUID();

    @Test
    void flatOptions_noBlankIndexKey_producesUnchangedFlatList() {
        String optionsJson = "["
                + "{\"text\":\"London\",\"correct\":false,\"orderIndex\":0},"
                + "{\"text\":\"Paris\",\"correct\":true,\"orderIndex\":1},"
                + "{\"text\":\"Berlin\",\"correct\":false,\"orderIndex\":2}"
                + "]";

        TaskView task = toTask(optionsJson);

        assertThat(task.options()).containsExactly(
                new OptionView("London", "0"),
                new OptionView("Paris", "1"),
                new OptionView("Berlin", "2"));
        assertThat(task.blankGroups()).isNull();
    }

    @Test
    void flatOptions_explicitNullBlankIndex_producesUnchangedFlatList() {
        String optionsJson = "["
                + "{\"text\":\"London\",\"correct\":false,\"orderIndex\":0,\"blankIndex\":null},"
                + "{\"text\":\"Paris\",\"correct\":true,\"orderIndex\":1,\"blankIndex\":null}"
                + "]";

        TaskView task = toTask(optionsJson);

        assertThat(task.options()).containsExactly(
                new OptionView("London", "0"),
                new OptionView("Paris", "1"));
        assertThat(task.blankGroups()).isNull();
    }

    @Test
    void blankGroupedOptions_groupByBlankIndex_ascendingOrder() {
        String optionsJson = "["
                + "{\"text\":\"tragic\",\"correct\":true,\"orderIndex\":0,\"blankIndex\":0},"
                + "{\"text\":\"boring\",\"correct\":false,\"orderIndex\":1,\"blankIndex\":0},"
                + "{\"text\":\"happy\",\"correct\":false,\"orderIndex\":2,\"blankIndex\":0},"
                + "{\"text\":\"nastiness\",\"correct\":true,\"orderIndex\":3,\"blankIndex\":1},"
                + "{\"text\":\"kindness\",\"correct\":false,\"orderIndex\":4,\"blankIndex\":1},"
                + "{\"text\":\"silence\",\"correct\":false,\"orderIndex\":5,\"blankIndex\":1}"
                + "]";

        TaskView task = toTask(optionsJson);

        assertThat(task.options()).isNull();
        assertThat(task.blankGroups()).containsExactly(
                new BlankGroupView(0, List.of(
                        new OptionView("tragic", "0"),
                        new OptionView("boring", "1"),
                        new OptionView("happy", "2"))),
                new BlankGroupView(1, List.of(
                        new OptionView("nastiness", "3"),
                        new OptionView("kindness", "4"),
                        new OptionView("silence", "5"))));
    }

    @Test
    void emptyOptions_nullBlankAndOptOptions() {
        assertThat(toTask(null).options()).isNull();
        assertThat(toTask(null).blankGroups()).isNull();

        assertThat(toTask("").options()).isNull();
        assertThat(toTask("").blankGroups()).isNull();

        assertThat(toTask("   ").options()).isNull();
        assertThat(toTask("   ").blankGroups()).isNull();

        assertThat(toTask("[]").options()).isNull();
        assertThat(toTask("[]").blankGroups()).isNull();
    }

    @Test
    void orderIndex_roundTripsAsExactDecimalString_flatAndGrouped() {
        TaskView flat = toTask("[{\"text\":\"x\",\"correct\":false,\"orderIndex\":12}]");
        assertThat(flat.options().get(0).orderIndex()).isEqualTo("12");

        TaskView grouped = toTask("[{\"text\":\"y\",\"correct\":false,\"orderIndex\":7,\"blankIndex\":0}]");
        assertThat(grouped.blankGroups().get(0).options().get(0).orderIndex()).isEqualTo("7");
    }

    @Test
    void mixedBlankIndex_throwsIllegalStateException_notNullPointerException() {
        String optionsJson = "["
                + "{\"text\":\"a\",\"correct\":false,\"orderIndex\":0},"
                + "{\"text\":\"b\",\"correct\":false,\"orderIndex\":1,\"blankIndex\":0}"
                + "]";

        assertThatThrownBy(() -> toTask(optionsJson))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(pinnedItemPublicId.toString())
                .hasMessageContaining("mix of blank-grouped and ungrouped");
    }

    private TaskView toTask(String optionsJson) {
        PinnedItemView item = new PinnedItemView(
                pinnedItemPublicId, 0, "READING", "MC_READING_SINGLE", "Sample title", "Sample prompt",
                null, null, null, null, null, null, optionsJson, 30, 60);

        ExamAttempt attempt = new ExamAttempt();
        attempt.setPublicId(UUID.randomUUID());
        attempt.setStatus(AttemptStatus.IN_PROGRESS);

        Instant now = Instant.now();
        TimerState timer = new TimerState();
        timer.setPhase(TimerPhase.RESPONSE);
        timer.setPrepDeadline(now);
        timer.setResponseDeadline(now.plusSeconds(60));

        AttemptTaskResponse response = mapper.toTaskResponse(attempt, item, timer, 5);
        return response.task();
    }
}
