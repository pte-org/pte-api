package com.pte.itembank.internal.generation.domain;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobStatusTest {

    @Test
    void openAndTerminalSplitEveryStatus() {
        EnumSet<JobStatus> union = EnumSet.copyOf(JobStatus.OPEN);
        union.addAll(JobStatus.TERMINAL);

        assertThat(union).containsExactlyInAnyOrder(JobStatus.values());
        assertThat(JobStatus.OPEN).doesNotContainAnyElementsOf(JobStatus.TERMINAL);
    }

    @Test
    void pendingIsOpenButNeitherActiveNorLeased() {
        assertThat(JobStatus.OPEN).contains(JobStatus.PENDING);
        assertThat(JobStatus.ACTIVE).doesNotContain(JobStatus.PENDING);
        assertThat(JobStatus.LEASED).doesNotContain(JobStatus.PENDING);
    }

    @Test
    void activeCountsAJobStillStoppingForAPause() {
        // A PAUSING child still holds its lease and its slot until its worker hands it back.
        assertThat(JobStatus.ACTIVE).containsExactlyInAnyOrder(JobStatus.QUEUED, JobStatus.RUNNING,
                JobStatus.CANCELLING, JobStatus.PAUSING);
        assertThat(JobStatus.LEASED)
                .containsExactlyInAnyOrder(JobStatus.RUNNING, JobStatus.CANCELLING, JobStatus.PAUSING);
        assertThat(JobStatus.OPEN).containsAll(JobStatus.ACTIVE);
    }

    @Test
    void batchOpenAndTerminalSplitEveryBatchStatus() {
        EnumSet<BatchStatus> union = EnumSet.copyOf(BatchStatus.OPEN);
        union.addAll(BatchStatus.TERMINAL);

        assertThat(union).containsExactlyInAnyOrder(BatchStatus.values());
        // Mirrors the one-open-batch index (V60): a paused batch still holds its requester's open batch.
        assertThat(BatchStatus.OPEN).containsExactlyInAnyOrder(BatchStatus.QUEUED, BatchStatus.RUNNING,
                BatchStatus.CANCELLING, BatchStatus.PAUSING, BatchStatus.PAUSED);
        assertThat(BatchStatus.HALTED).containsExactlyInAnyOrder(BatchStatus.PAUSING, BatchStatus.PAUSED);
        assertThat(BatchStatus.CANCELLABLE).doesNotContain(BatchStatus.CANCELLING)
                .doesNotContainAnyElementsOf(BatchStatus.TERMINAL);
    }

    @Test
    void batchRowDefaultsMissingHintsAndImageKindAndCopiesHints() {
        List<String> hints = new java.util.ArrayList<>(List.of("cities"));
        BatchRow row = new BatchRow("DESCRIBE_IMAGE", null, 3, hints, null);
        hints.add("later");

        assertThat(row.topicHints()).containsExactly("cities");
        assertThat(row.imageKind()).isEqualTo(ImageKind.AUTO);
        assertThat(new BatchRow("X", null, 1, null, ImageKind.CHART).topicHints()).isEmpty();
    }
}
