package com.pte.notification.internal.repository;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

/** Migrates the test's private schema with the real Flyway baseline and adds the users the inbox tests refer to. */
final class InboxTestSchema {
    private InboxTestSchema() {
    }

    static void load(DataSource dataSource, String schema, UUID... users) {
        Flyway.configure().dataSource(dataSource).schemas(schema).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        for (UUID user : users) {
            jdbc.update("""
                    INSERT INTO users (public_id, username, email, full_name, credential_hash, status, deleted,
                                       must_change_password, created_at, updated_at)
                    VALUES (?, ?, ?, 'Inbox Test', 'x', 'ACTIVE', false, false, now(), now())
                    """, user, user + "@inbox.test", user + "@inbox.test");
        }
    }
}
