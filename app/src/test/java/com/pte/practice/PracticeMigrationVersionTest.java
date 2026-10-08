package com.pte.practice;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeMigrationVersionTest {

    @Test
    void identityMigrationUsesHashedEmailAndForwardOnlyPracticeStorage() throws IOException {
        String sql = new ClassPathResource("db/migration/V82__practice_identity_and_entitlement.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql).contains("normalized_email_hash VARCHAR(64)")
                .contains("status VARCHAR(16) NOT NULL")
                .contains("linked_at TIMESTAMP WITH TIME ZONE NOT NULL")
                .contains("code_hash VARCHAR(255) NOT NULL")
                .contains("-- Rollback policy: disable the practice auth feature")
                .doesNotContain("normalized_email VARCHAR");
    }

    @Test
    void catalogSessionMigrationKeepsPracticeSeparateAndConfidenceAdditive() throws IOException {
        String sql = new ClassPathResource("db/migration/V83__practice_catalog_and_session_boundary.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql).contains("CREATE TABLE practice_sessions")
                .contains("identity_public_id UUID NOT NULL")
                .contains("source_type VARCHAR(16) NOT NULL")
                .contains("CREATE TABLE practice_session_attempts")
                .contains("CREATE TABLE practice_session_operations")
                .contains("uk_practice_session_operation_key")
                .contains("ALTER TABLE attempt_answers")
                .contains("ADD COLUMN confidence VARCHAR(16)")
                .contains("-- Rollback policy: disable practice entry")
                .doesNotContain("DROP TABLE");
    }

    @Test
    void sessionItemsMigrationIsAdditiveAndDoesNotStoreCorrectAnswers() throws IOException {
        String sql = new ClassPathResource("db/migration/V84__practice_session_items_and_activity.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql).contains("ADD COLUMN last_activity_at")
                .contains("CREATE TABLE practice_session_items")
                .contains("saved_payload TEXT")
                .contains("uk_practice_session_item_order")
                .contains("-- Rollback policy: disable practice entry")
                .doesNotContain("correct_answer")
                .doesNotContain("DROP TABLE");
    }

    @Test
    void responseMediaBindingMigrationIsAdditiveAndKeepsRollbackNonDestructive() throws IOException {
        String sql = new ClassPathResource("db/migration/V85__practice_response_media_binding.sql")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(sql).contains("practice_session_public_id UUID")
                .contains("practice_item_public_id UUID")
                .contains("purpose VARCHAR(64)")
                .contains("idx_media_objects_practice_binding")
                .contains("-- Rollback policy: retain media rows")
                .doesNotContain("DROP TABLE")
                .doesNotContain("DELETE FROM");
    }
}
