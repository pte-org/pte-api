package com.pte.scoring.internal.rubric.support;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;

/** Shared helpers for the rubric Postgres tests (container image and Flyway wiring). */
public final class RubricPostgres {

    public static final String IMAGE = "postgres:16-alpine";
    public static final String MIGRATIONS = "classpath:db/migration";

    private RubricPostgres() {
    }

    public static DataSource dataSource(PostgreSQLContainer container) {
        return new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    /** Migrates up to and including {@code targetVersion}. */
    public static void migrateTo(DataSource dataSource, String targetVersion) {
        Flyway.configure().dataSource(dataSource).locations(MIGRATIONS).baselineOnMigrate(false)
                .target(targetVersion).load().migrate();
    }

    /** Migrates to the latest version. */
    public static void migrateAll(DataSource dataSource) {
        Flyway.configure().dataSource(dataSource).locations(MIGRATIONS).baselineOnMigrate(false).load().migrate();
    }
}
