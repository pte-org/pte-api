package com.aptis.modules.examoperations.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImportBatchTest {

    @Test
    @DisplayName("create() sets PENDING status and timestamps")
    void createSetsPendingStatus() {
        ImportBatch batch = ImportBatch.create("host1", "STUDENT", "roster.xlsx");

        assertThat(batch.getHostId()).isEqualTo("host1");
        assertThat(batch.getType()).isEqualTo("STUDENT");
        assertThat(batch.getFilename()).isEqualTo("roster.xlsx");
        assertThat(batch.getStatus()).isEqualTo(ImportBatch.STATUS_PENDING);
        assertThat(batch.getCreatedAt()).isNotNull();
        assertThat(batch.getExamId()).isNull();
    }

    @Test
    @DisplayName("markCompleted() updates counts, status, and timestamp")
    void markCompletedUpdatesFields() {
        ImportBatch batch = ImportBatch.create("host1", "STUDENT", "roster.xlsx");

        batch.markCompleted(10, 2, "[{\"row\":3,\"field\":\"email\",\"reason\":\"invalid\"}]");

        assertThat(batch.getValidCount()).isEqualTo(10);
        assertThat(batch.getErrorCount()).isEqualTo(2);
        assertThat(batch.getProcessedRecords()).isEqualTo(12);
        assertThat(batch.getTotalRecords()).isEqualTo(12);
        assertThat(batch.getStatus()).isEqualTo(ImportBatch.STATUS_SUCCESS);
        assertThat(batch.getCompletedAt()).isNotNull();
        assertThat(batch.getErrorsJson()).contains("invalid");
    }

    @Test
    @DisplayName("markCompleted() with zero errors stores empty array JSON")
    void markCompletedZeroErrors() {
        ImportBatch batch = ImportBatch.create("host1", "STUDENT", "roster.xlsx");

        batch.markCompleted(5, 0, "[]");

        assertThat(batch.getValidCount()).isEqualTo(5);
        assertThat(batch.getErrorCount()).isZero();
        assertThat(batch.getErrorsJson()).isEqualTo("[]");
    }

    @Test
    @DisplayName("examId starts null, can be set later")
    void examIdNullable() {
        ImportBatch batch = ImportBatch.create("host1", "STUDENT", "roster.xlsx");

        assertThat(batch.getExamId()).isNull();

        batch.setExamId(42L);
        assertThat(batch.getExamId()).isEqualTo(42L);

        // Idempotent reassign
        batch.setExamId(99L);
        assertThat(batch.getExamId()).isEqualTo(99L);
    }
}
