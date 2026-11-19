package com.pte.itembank.internal.generation.batch;

import org.flywaydb.core.Flyway;

import java.util.regex.Pattern;

/**
 * The dedicated, throwaway Postgres database of the batch integration tests. Only a local database whose name is
 * {@code pte_lifecycle_test} is accepted, so a run can never migrate or wipe the application database ({@code pte}).
 * Run with {@code -Dlifecycle.test.db.url=jdbc:postgresql://localhost:5432/pte_lifecycle_test}
 * {@code -Dlifecycle.test.db.user=...} {@code -Dlifecycle.test.db.password=...}.
 */
final class BatchPostgresDatabase {

    static final String URL_PROPERTY = "lifecycle.test.db.url";
    private static final Pattern ALLOWED =
            Pattern.compile("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):\\d+/pte_lifecycle_test");

    private BatchPostgresDatabase() {
    }

    static String url() {
        String url = System.getProperty(URL_PROPERTY, "");
        if (!ALLOWED.matcher(url).matches()) {
            throw new IllegalArgumentException("Only the dedicated local pte_lifecycle_test database is permitted");
        }
        return url;
    }

    static String user() {
        return System.getProperty("lifecycle.test.db.user", "postgres");
    }

    static String password() {
        return System.getProperty("lifecycle.test.db.password", "");
    }

    /** Brings {@code schema} of the dedicated database to {@code target} ("latest" for every migration). */
    static void migrate(String schema, String target) {
        Flyway.configure()
                .dataSource(url(), user(), password())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }
}
