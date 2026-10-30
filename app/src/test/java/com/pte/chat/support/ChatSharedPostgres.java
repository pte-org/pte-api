package com.pte.chat.support;

import com.zaxxer.hikari.HikariDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;

/**
 * One Postgres container per test JVM, migrated once with the real Flyway scripts. Starting a container per
 * test class would cost several seconds each; the tests isolate themselves with random student and session
 * ids or by truncating the chat tables. Testcontainers' reaper removes the container when the JVM exits.
 * Callers must already be skipped when Docker is missing (see {@code @Testcontainers(disabledWithoutDocker)}).
 */
public final class ChatSharedPostgres {

    private static final int POOL_SIZE = 60;

    private static PostgreSQLContainer container;
    private static HikariDataSource pool;

    private ChatSharedPostgres() {
    }

    public static synchronized PostgreSQLContainer container() {
        if (container == null) {
            PostgreSQLContainer started = new PostgreSQLContainer(ChatPostgres.IMAGE);
            started.start();
            ChatPostgres.flyway(ChatPostgres.dataSource(started)).migrate();
            container = started;
        }
        return container;
    }

    /** A pooled data source with room for the 50-thread tests. */
    public static synchronized DataSource dataSource() {
        if (pool == null) {
            PostgreSQLContainer postgres = container();
            HikariDataSource created = new HikariDataSource();
            created.setJdbcUrl(postgres.getJdbcUrl());
            created.setUsername(postgres.getUsername());
            created.setPassword(postgres.getPassword());
            created.setMaximumPoolSize(POOL_SIZE);
            pool = created;
        }
        return pool;
    }
}
