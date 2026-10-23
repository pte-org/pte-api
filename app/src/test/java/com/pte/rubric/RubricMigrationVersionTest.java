package com.pte.rubric;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RubricMigrationVersionTest {

    @Test
    void rubricMigrationIsAdditiveAndPinsHistoricalProvenance() throws IOException {
        String sql = new ClassPathResource("db/migration/V122__academic_rubric_revisions.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS rubric_revisions")
                .contains("CREATE TABLE IF NOT EXISTS rubric_criteria")
                .contains("CREATE TABLE IF NOT EXISTS rubric_criterion_levels")
                .contains("uk_rubric_published_task_channel")
                .contains("rubric_revision_public_id UUID")
                .contains("rubric_canonical_hash VARCHAR(64)")
                .doesNotContain("DROP TABLE")
                .doesNotContain("DELETE FROM");
    }
}
