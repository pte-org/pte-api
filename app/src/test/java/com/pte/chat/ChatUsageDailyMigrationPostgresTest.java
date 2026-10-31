package com.pte.chat;

import com.pte.chat.support.ChatPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V16 swaps the stored chat content for a content-free daily table. The counts that already exist must survive
 * the swap, and everything a student typed or was answered must be gone afterwards. This is the destructive
 * migration, so it is run on a database that really holds chat rows before it.
 */
@Testcontainers(disabledWithoutDocker = true)
class ChatUsageDailyMigrationPostgresTest {

    private static final String BEFORE_CHAT_SWAP = "15";
    private static final UUID STUDENT = UUID.randomUUID();
    private static final UUID TENANT = UUID.randomUUID();

    private static void message(JdbcTemplate jdbc, String createdAt, String status, String model, int in, int out,
                                int thinking, boolean fromCache, boolean deleted) {
        jdbc.update("INSERT INTO chat_message (public_id, created_at, updated_at, deleted, session_id, student_id, "
                        + "tenant_id, question, answer, tokens_in, tokens_out, tokens_thinking, from_cache, status, model) "
                        + "VALUES (?, ?::timestamptz, ?::timestamptz, ?, ?, ?, ?, 'secret question', 'secret answer', "
                        + "?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), createdAt, createdAt, deleted, UUID.randomUUID(), STUDENT, TENANT, in, out,
                thinking, fromCache, status, model);
    }

    private static boolean tableExists(JdbcTemplate jdbc, String name) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT to_regclass('public." + name + "') IS NOT NULL", Boolean.class));
    }

    @Test
    void keepsTheDailyCountsAndDropsEveryStoredChat() {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(ChatPostgres.IMAGE)) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(),
                    postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(dataSource).locations(ChatPostgres.MIGRATIONS).target(BEFORE_CHAT_SWAP)
                    .load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);

            // 2026-10-28 23:30 UTC is 06:30 on the 29th in Vietnam: the day is the Vietnam day.
            message(jdbc, "2026-10-28T10:00:00Z", "OK", "gemini-2.5-flash-lite", 1000, 200, 50, false, false);
            message(jdbc, "2026-10-28T11:00:00Z", "REFUSED", "gemini-2.5-flash-lite", 500, 10, 0, false, false);
            message(jdbc, "2026-10-28T12:00:00Z", "FAILED", "gemini-2.5-flash-lite", 0, 0, 0, false, false);
            message(jdbc, "2026-10-28T23:30:00Z", "OK", "gemini-3.5-flash-lite", 2000, 400, 100, false, false);
            message(jdbc, "2026-10-28T13:00:00Z", "OK", "gemini-2.5-flash-lite", 0, 0, 0, true, false);
            message(jdbc, "2026-10-28T14:00:00Z", "OK", "gemini-2.5-flash-lite", 9999, 9999, 9999, false, true);
            jdbc.update("INSERT INTO chat_usage (student_id, usage_date, used) VALUES (?, DATE '2026-10-28', 4)",
                    STUDENT);

            Flyway.configure().dataSource(dataSource).locations(ChatPostgres.MIGRATIONS).load().migrate();

            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM chat_usage_daily ORDER BY usage_date, model");
            assertThat(rows).hasSize(2);
            Map<String, Object> oldModel = rows.get(0);
            assertThat(oldModel.get("usage_date").toString()).isEqualTo("2026-10-28");
            assertThat(oldModel.get("model")).isEqualTo("gemini-2.5-flash-lite");
            assertThat(oldModel).containsEntry("messages", 4).containsEntry("ok", 2).containsEntry("refused", 1)
                    .containsEntry("failed", 1).containsEntry("tokens_in", 1500L).containsEntry("tokens_out", 210L)
                    .containsEntry("tokens_thinking", 50L);
            // (1500 * 0.10 + (210 + 50) * 0.40) / 1,000,000, the prices the report used until now.
            assertThat((BigDecimal) oldModel.get("cost_usd")).isEqualByComparingTo("0.000254");
            Map<String, Object> newModel = rows.get(1);
            assertThat(newModel.get("usage_date").toString()).isEqualTo("2026-10-29");
            assertThat(newModel.get("model")).isEqualTo("gemini-3.5-flash-lite");
            assertThat(newModel).containsEntry("messages", 1).containsEntry("ok", 1);

            for (String gone : List.of("chat_message", "chat_session", "chat_consent", "chat_answer_cache")) {
                assertThat(tableExists(jdbc, gone)).as(gone + " is dropped").isFalse();
            }
            assertThat(jdbc.queryForObject("SELECT used FROM chat_usage WHERE student_id = ?", Integer.class, STUDENT))
                    .as("the daily quota counter stays").isEqualTo(4);
        }
    }

    @Test
    void worksOnADatabaseWithNoChatAtAll() {
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(ChatPostgres.IMAGE)) {
            postgres.start();
            DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(),
                    postgres.getUsername(), postgres.getPassword());

            Flyway.configure().dataSource(dataSource).locations(ChatPostgres.MIGRATIONS).load().migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_usage_daily", Integer.class)).isZero();
        }
    }
}
