package com.pte.identity.internal.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in migration rehearsal. The caller must provide a dedicated local
 * PostgreSQL database; the test refuses every other URL and cleans only its
 * unique temporary schema.
 */
@EnabledIfSystemProperty(
        named = "platform.role.migration.db.url",
        matches = "jdbc:postgresql://(127\\.0\\.0\\.1|pte-postgres):5432/pte_role_migration_test")
class PlatformRoleMigrationPostgresIntegrationTest {

    @Test
    void legacyAuthorRowIsCanonicalizedAfterTheCompatibilityWindow() throws Exception {
        String url = System.getProperty("platform.role.migration.db.url");
        if (!Set.of(
                "jdbc:postgresql://127.0.0.1:5432/pte_role_migration_test",
                "jdbc:postgresql://pte-postgres:5432/pte_role_migration_test").contains(url)) {
            throw new IllegalArgumentException("Only the dedicated local role migration database is permitted");
        }

        String username = requiredProperty("platform.role.migration.db.user", "PTE_ROLE_MIGRATION_DB_USER");
        String password = System.getProperty("platform.role.migration.db.password",
                System.getenv().getOrDefault("PTE_ROLE_MIGRATION_DB_PASSWORD", ""));
        String schema = "legacy_role_" + UUID.randomUUID().toString().replace("-", "");
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url, username, password);
        JdbcTemplate schemaJdbc = null;
        Connection cleanupConnection = null;

        try {
            cleanupConnection = dataSource.getConnection();
            schemaJdbc = new JdbcTemplate(
                    new DriverManagerDataSource(url + "?currentSchema=" + schema, username, password));
            Flyway.configure().dataSource(dataSource).schemas(schema)
                    .locations("classpath:db/migration").target("88").load().migrate();
            UUID publicId = UUID.randomUUID();
            schemaJdbc.update("""
                    insert into users (public_id, created_at, updated_at, deleted, username, email, status)
                    values (?, now(), now(), false, ?, ?, 'ACTIVE')
                    """, publicId, "legacy-author-" + publicId, "legacy-author-" + publicId);
            Long userId = schemaJdbc.queryForObject("select id from users where public_id = ?", Long.class, publicId);
            schemaJdbc.update("insert into user_roles (user_id, role) values (?, 'PLATFORM_AUTHOR')", userId);

            Flyway.configure().dataSource(dataSource).schemas(schema)
                    .locations("classpath:db/migration").load().migrate();

            assertThat(schemaJdbc.queryForObject(
                    "select role from user_roles where user_id = ?", String.class, userId))
                    .isEqualTo("ACADEMIC_STAFF");
        } finally {
            if (cleanupConnection != null) {
                try (Statement statement = cleanupConnection.createStatement()) {
                    statement.execute("drop schema if exists \"" + schema + "\" cascade");
                } finally {
                    cleanupConnection.close();
                }
            }
        }
    }

    private static String requiredProperty(String property, String environment) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            value = System.getenv(environment);
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + property + " or " + environment);
        }
        return value;
    }
}
