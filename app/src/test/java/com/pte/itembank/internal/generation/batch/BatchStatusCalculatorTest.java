package com.pte.itembank.internal.generation.batch;

import com.pte.itembank.internal.generation.domain.BatchStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BatchStatusCalculatorTest {

    @Test
    void aCancelRequestAlwaysEndsCancelled() {
        assertThat(BatchStatusCalculator.of(true, 10, 10)).isEqualTo(BatchStatus.CANCELLED);
    }

    @Test
    void everyQuestionSavedIsCompleted() {
        assertThat(BatchStatusCalculator.of(false, 10, 10)).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    void someSavedIsPartialAndNoneSavedIsFailed() {
        assertThat(BatchStatusCalculator.of(false, 3, 10)).isEqualTo(BatchStatus.PARTIALLY_COMPLETED);
        assertThat(BatchStatusCalculator.of(false, 0, 10)).isEqualTo(BatchStatus.FAILED);
    }
}
