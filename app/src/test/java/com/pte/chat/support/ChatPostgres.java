package com.pte.chat.support;

import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;

/** Shared helpers for the chat Postgres tests (container image and Flyway wiring). */
public final class ChatPostgres {

    public static final String IMAGE = "postgres:16-alpine";
    public static final String MIGRATIONS = "classpath:db/migration";

    private ChatPostgres() {
    }

    public static DataSource dataSource(PostgreSQLContainer container) {
        return new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    /** Flyway as configured by the postgres-test profile, but built programmatically. */
    public static Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(MIGRATIONS)
                .baselineOnMigrate(false)
                .load();
    }
}
