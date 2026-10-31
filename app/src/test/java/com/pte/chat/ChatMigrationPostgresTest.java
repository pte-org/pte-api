package com.pte.chat;

import com.pte.chat.support.ChatPostgres;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real Flyway migrations (through V7) on a throwaway Postgres 16 and
 * proves the chat schema constraints and the atomic quota statement (AD-04).
 * Skipped when Docker is unavailable; the manual {@code psql} gate in Phase 5
 * is then mandatory.
 */
@Testcontainers(disabledWithoutDocker = true)
class ChatMigrationPostgresTest {

    private static final String SQLSTATE_UNIQUE_VIOLATION = "23505";
    private static final String SQLSTATE_CHECK_VIOLATION = "23514";

    /** AD-04 quota statement, verbatim from the design. */
    private static final String QUOTA_SQL = "INSERT INTO chat_usage AS u (student_id, usage_date, used) "
            + "VALUES (?, ?, 1) "
            + "ON CONFLICT (student_id, usage_date) DO UPDATE SET used = u.used + 1 WHERE u.used < ? "
            + "RETURNING u.used";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(ChatPostgres.IMAGE);

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        Flyway flyway = ChatPostgres.flyway(ChatPostgres.dataSource(POSTGRES));
        flyway.migrate();
        jdbc = new JdbcTemplate(ChatPostgres.dataSource(POSTGRES));
    }

    @Test
    void flyway_reaches_v7_cleanly_with_no_pending_or_failed_migration() {
        Flyway flyway = ChatPostgres.flyway(ChatPostgres.dataSource(POSTGRES));

        MigrationInfo v7 = Arrays.stream(flyway.info().all())
                .filter(info -> info.getVersion() != null && "7".equals(info.getVersion().getVersion()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("migration V7 not found on the classpath"));

        assertThat(v7.getState()).isEqualTo(MigrationState.SUCCESS);
        assertThat(v7.getScript()).isEqualTo("V7__chat_usage_and_messages.sql");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(Arrays.stream(flyway.info().all()).filter(info -> info.getState().isFailed())).isEmpty();
    }

    @Test
    void the_four_chat_tables_exist() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name IN ('chat_usage', 'chat_session', 'chat_message', 'chat_answer_cache')
                """, String.class);

        assertThat(tables).containsExactlyInAnyOrder("chat_usage", "chat_session", "chat_message",
                "chat_answer_cache");
    }

    @Test
    void chat_usage_rejects_a_second_row_for_the_same_student_and_date() {
        UUID student = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 28);
        jdbc.update("INSERT INTO chat_usage (student_id, usage_date, used) VALUES (?, ?, 1)", student, date);

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO chat_usage (student_id, usage_date, used) VALUES (?, ?, 1)", student, date))
                .satisfies(error -> assertThat(sqlState(error)).isEqualTo(SQLSTATE_UNIQUE_VIOLATION));

        jdbc.update("INSERT INTO chat_usage (student_id, usage_date, used) VALUES (?, ?, 1)",
                student, date.plusDays(1));
    }

    @Test
    void chat_usage_rejects_negative_used() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO chat_usage (student_id, usage_date, used) VALUES (?, ?, -1)",
                UUID.randomUUID(), LocalDate.of(2026, 10, 28)))
                .satisfies(error -> assertThat(sqlState(error)).isEqualTo(SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void chat_message_accepts_the_three_statuses_and_a_null_on_topic() {
        for (String status : List.of("OK", "REFUSED", "FAILED")) {
            insertMessage(status);
        }

        Integer nullOnTopic = jdbc.queryForObject(
                "SELECT count(*) FROM chat_message WHERE on_topic IS NULL", Integer.class);
        assertThat(nullOnTopic).isGreaterThanOrEqualTo(3);
    }

    @Test
    void chat_message_rejects_a_status_outside_the_allowed_set() {
        assertThatThrownBy(() -> insertMessage("WEIRD"))
                .satisfies(error -> assertThat(sqlState(error)).isEqualTo(SQLSTATE_CHECK_VIOLATION));
    }

    @Test
    void chat_session_and_answer_cache_accept_rows_and_cache_key_is_unique() {
        jdbc.update("INSERT INTO chat_session (session_id, student_id, created_at) VALUES (?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), Timestamp.from(Instant.now()));

        String key = "e".repeat(64);
        insertCache(key);

        assertThatThrownBy(() -> insertCache(key))
                .satisfies(error -> assertThat(sqlState(error)).isEqualTo(SQLSTATE_UNIQUE_VIOLATION));
    }

    @Test
    void quota_statement_returns_1_to_limit_then_zero_rows() {
        UUID student = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 28);
        int limit = 3;

        for (int expected = 1; expected <= limit; expected++) {
            assertThat(consumeQuota(student, date, limit)).containsExactly(expected);
        }

        assertThat(consumeQuota(student, date, limit)).isEmpty();
        assertThat(consumeQuota(student, date, limit)).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT used FROM chat_usage WHERE student_id = ? AND usage_date = ?",
                Integer.class, student, date)).isEqualTo(limit);
    }

    @Test
    void quota_statement_is_scoped_per_student_and_per_day() {
        UUID student = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 28);

        assertThat(consumeQuota(student, date, 1)).containsExactly(1);
        assertThat(consumeQuota(student, date, 1)).isEmpty();
        assertThat(consumeQuota(student, date.plusDays(1), 1)).containsExactly(1);
        assertThat(consumeQuota(UUID.randomUUID(), date, 1)).containsExactly(1);
    }

    @Test
    void concurrent_quota_calls_grant_exactly_the_limit() throws Exception {
        int threads = 12;
        int limit = 5;
        UUID student = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 10, 28);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<List<Integer>>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<List<Integer>> task = () -> {
                    ready.countDown();
                    go.await();
                    return consumeQuota(student, date, limit);
                };
                futures.add(executor.submit(task));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            List<Integer> granted = new ArrayList<>();
            for (Future<List<Integer>> future : futures) {
                granted.addAll(future.get(60, TimeUnit.SECONDS));
            }

            assertThat(granted).hasSize(limit);
            assertThat(granted).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject(
                "SELECT used FROM chat_usage WHERE student_id = ? AND usage_date = ?",
                Integer.class, student, date)).isEqualTo(limit);
    }

    private static List<Integer> consumeQuota(UUID student, LocalDate date, int limit) {
        return jdbc.queryForList(QUOTA_SQL, Integer.class, student, date, limit);
    }

    private static void insertMessage(String status) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO chat_message (public_id, created_at, updated_at, deleted, session_id, student_id,
                                          tenant_id, question, answer, from_cache, on_topic, status)
                VALUES (?, ?, ?, FALSE, ?, ?, ?, 'How do I improve?', '', FALSE, NULL, ?)
                """, UUID.randomUUID(), now, now, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), status);
    }

    private static void insertCache(String key) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("""
                INSERT INTO chat_answer_cache (public_id, created_at, updated_at, deleted, cache_key, answer,
                                               prompt_version, model)
                VALUES (?, ?, ?, FALSE, ?, 'answer', 'v1', 'gemini-2.5-flash-lite')
                """, UUID.randomUUID(), now, now, key);
    }

    private static String sqlState(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
        }
        return null;
    }
}
