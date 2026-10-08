package com.pte.academic;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AcademicAuthorOwnershipMigrationTest {

    @Test
    void migrationAddsNullableOwnershipForAllAcademicAggregatesAndIndexes() throws IOException {
        String sql;
        try (var input = new ClassPathResource("db/migration/V88__academic_author_ownership.sql")
                .getInputStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("questions", "question_types", "score_templates", "exam_blueprints");
        assertThat(sql).contains("author_user_public_id");
        assertThat(sql).contains("idx_questions_academic_author");
        assertThat(sql).contains("idx_question_types_academic_author");
        assertThat(sql).contains("idx_score_templates_academic_author");
        assertThat(sql).contains("idx_exam_blueprints_academic_author");
    }
}
