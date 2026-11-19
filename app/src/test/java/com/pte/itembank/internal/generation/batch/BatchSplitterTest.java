package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.internal.generation.batch.BatchSplitter.ChildPlan;
import com.pte.itembank.internal.generation.domain.BatchRow;
import com.pte.itembank.internal.generation.domain.ImageKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BatchSplitterTest {

    private static BatchRow row(int count, String... hints) {
        return new BatchRow("MC_READING_SINGLE", CefrLevel.B2, count, List.of(hints), ImageKind.AUTO);
    }

    @Test
    void oneQuestionIsOneChild() {
        assertThat(BatchSplitter.split(List.of(row(1)), 10))
                .containsExactly(new ChildPlan(0, 0, null, 1));
    }

    @Test
    void tenQuestionsFitOneChildAndElevenNeedTwo() {
        assertThat(BatchSplitter.split(List.of(row(10)), 10)).extracting(ChildPlan::count).containsExactly(10);
        assertThat(BatchSplitter.split(List.of(row(11)), 10)).extracting(ChildPlan::count).containsExactly(10, 1);
    }

    @Test
    void questionsAreSpreadEvenlyOverTheTopics() {
        List<ChildPlan> children = BatchSplitter.split(List.of(row(25, "a", "b", "c")), 10);

        assertThat(children).extracting(ChildPlan::count).containsExactly(9, 8, 8);
        assertThat(children).extracting(ChildPlan::topicHint).containsExactly("a", "b", "c");
    }

    @Test
    void fewerQuestionsThanTopicsSkipsTheEmptyTopics() {
        assertThat(BatchSplitter.split(List.of(row(2, "a", "b", "c")), 10))
                .extracting(ChildPlan::topicHint).containsExactly("a", "b");
    }

    @Test
    void aPerJobSettingAboveTheDatabaseLimitIsClampedToTen() {
        assertThat(BatchSplitter.split(List.of(row(50)), 50)).hasSize(5)
                .allSatisfy(child -> assertThat(child.count()).isEqualTo(10));
        assertThat(BatchSplitter.split(List.of(row(3)), 0)).hasSize(3);
    }

    @Test
    void runOrderIsContinuousAcrossRowsInRowOrder() {
        List<ChildPlan> children = BatchSplitter.split(List.of(row(12), row(3, "x")), 10);

        assertThat(children).extracting(ChildPlan::seq).containsExactly(0, 1, 2);
        assertThat(children).extracting(ChildPlan::rowIndex).containsExactly(0, 0, 1);
        assertThat(children.stream().mapToInt(ChildPlan::count).sum()).isEqualTo(15);
    }
}
