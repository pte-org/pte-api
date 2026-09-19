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
}
