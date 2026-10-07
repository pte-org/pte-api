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
}
