package com.pte.support;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SupportFeedbackInboxMigrationTest {
    @Test
    void migrationWidensInboxCheckConstraintsForSupportNotifications() throws IOException {
        ClassPathResource resource = new ClassPathResource("db/migration/V76__support_feedback_inbox.sql");
        assertThat(resource.exists()).isTrue();
        String sql;
        try (InputStream input = resource.getInputStream()) {
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql).contains("SUPPORT_TICKET_SUBMITTED", "SUPPORT_TICKET_NOTE_ADDED",
                "SUPPORT_TICKET_STATUS_CHANGED", "SUPPORT", "SUPPORT_TICKET");
        assertThat(sql).contains("notification_inbox_contents_notification_type_check",
                "notification_inbox_contents_category_check", "notification_inbox_contents_target_type_check");
    }
}
