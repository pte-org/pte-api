package com.pte.itembank;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionTypeCatalogMigrationTest {

    @Test
    void catalogMigrationDoesNotSeedQuestionTypes() throws IOException {
        try (InputStream input = getClass().getResourceAsStream("/db/migration/V37__question_type_catalog.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).doesNotContain("INSERT INTO question_types");
            assertThat(sql).contains("FOREIGN KEY (pte_task_type) REFERENCES question_types (code) NOT VALID");
        }
    }

    @Test
    void standardCatalogMigrationSeedsEveryCanonicalTaskTypeIdempotently() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/V44__seed_standard_task_type_catalog.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains("ON CONFLICT (code) DO NOTHING");
            assertThat(sql).contains("PERSONAL_INTRODUCTION");
            assertThat(sql).contains("FILL_IN_THE_BLANKS_DROPDOWN");
            assertThat(sql).contains("FILL_IN_THE_BLANKS_DRAG_AND_DROP");
            assertThat(sql).contains("FILL_IN_THE_BLANKS_TYPE_IN");
            assertThat(sql).contains("Unknown question type catalog code(s)");
            assertThat(sql).doesNotContain("UPDATE question_types SET display_name");
        }
    }

    @Test
    void runtimeProfileMigrationSeedsTheProfileContractAndPinsLegacyTemplateRows() throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/db/migration/V45__task_runtime_profiles_and_template_pins.sql")) {
            assertThat(input).isNotNull();
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(sql).contains("CREATE TABLE IF NOT EXISTS task_runtime_profiles");
            assertThat(sql).contains("UNIQUE (task_type_code, profile_version)");
            assertThat(sql).contains("ON CONFLICT (task_type_code, profile_version) DO NOTHING");
            assertThat(sql).contains("ADD COLUMN IF NOT EXISTS runtime_renderer_key");
            assertThat(sql).contains("UPDATE score_template_items item");
            assertThat(sql).contains("runtime_profile_version IS NULL");
            assertThat(sql).contains("PERSONAL_INTRODUCTION");
            assertThat(sql).contains("WRITE_FROM_DICTATION");
        }
    }
}
