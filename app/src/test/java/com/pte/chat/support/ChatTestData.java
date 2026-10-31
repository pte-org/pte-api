package com.pte.chat.support;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Plain-SQL fixtures and probes for the chat Postgres tests; they never go through the code under test. */
public final class ChatTestData {

    private final NamedParameterJdbcTemplate jdbc;

    public ChatTestData(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void truncateAll() {
        jdbc.getJdbcOperations().execute(
                "TRUNCATE chat_message, chat_session, chat_usage, chat_answer_cache, chat_consent RESTART IDENTITY");
    }

    // ---- consent -----------------------------------------------------------------------------------------

    public int consentRows(UUID student) {
        return count("SELECT count(*) FROM chat_consent WHERE student_id = :s", new MapSqlParameterSource("s", student));
    }

    public Map<String, Object> consentRow(UUID student) {
        return jdbc.queryForMap("SELECT * FROM chat_consent WHERE student_id = :s", new MapSqlParameterSource("s", student));
    }

    public void insertConsent(UUID student, String policyVersion, Instant consentedAt) {
        jdbc.update("INSERT INTO chat_consent (student_id, policy_version, consented_at, age_confirmed) "
                        + "VALUES (:s, :v, :c, true)",
                new MapSqlParameterSource("s", student).addValue("v", policyVersion)
                        .addValue("c", Timestamp.from(consentedAt)));
    }

    // ---- usage -------------------------------------------------------------------------------------------

    /** The counter for the day, 0 when no row exists. */
    public int used(UUID student, LocalDate date) {
        List<Integer> rows = jdbc.query(
                "SELECT used FROM chat_usage WHERE student_id = :s AND usage_date = :d",
                new MapSqlParameterSource("s", student).addValue("d", date), (rs, n) -> rs.getInt(1));
        return rows.isEmpty() ? 0 : rows.getFirst();
    }

    public int usageRows(UUID student) {
        return count("SELECT count(*) FROM chat_usage WHERE student_id = :s", new MapSqlParameterSource("s", student));
    }

    public int usageRows(UUID student, LocalDate date) {
        return count("SELECT count(*) FROM chat_usage WHERE student_id = :s AND usage_date = :d",
                new MapSqlParameterSource("s", student).addValue("d", date));
    }

    public void setUsed(UUID student, LocalDate date, int used) {
        jdbc.update("INSERT INTO chat_usage (student_id, usage_date, used) VALUES (:s, :d, :u) "
                        + "ON CONFLICT (student_id, usage_date) DO UPDATE SET used = :u",
                new MapSqlParameterSource("s", student).addValue("d", date).addValue("u", used));
    }

    // ---- sessions ----------------------------------------------------------------------------------------

    public int sessionRows(UUID sessionId) {
        return count("SELECT count(*) FROM chat_session WHERE session_id = :id",
                new MapSqlParameterSource("id", sessionId));
    }

    public Optional<UUID> sessionOwner(UUID sessionId) {
        return jdbc.query("SELECT student_id FROM chat_session WHERE session_id = :id",
                new MapSqlParameterSource("id", sessionId), (rs, n) -> rs.getObject(1, UUID.class)).stream().findFirst();
    }

    public void insertSession(UUID sessionId, UUID student, Instant createdAt) {
        jdbc.update("INSERT INTO chat_session (session_id, student_id, created_at) VALUES (:id, :s, :c)",
                new MapSqlParameterSource("id", sessionId).addValue("s", student)
                        .addValue("c", Timestamp.from(createdAt)));
    }

    // ---- messages ----------------------------------------------------------------------------------------

    /** Inserts one message row directly; {@code onTopic} follows the status the way the service would store it. */
    public void insertMessage(UUID sessionId, UUID student, String question, String answer, String status,
                              Instant createdAt) {
        Boolean onTopic = switch (status) {
            case "OK" -> Boolean.TRUE;
            case "REFUSED" -> Boolean.FALSE;
            default -> null;
        };
        jdbc.update("INSERT INTO chat_message (public_id, created_at, updated_at, session_id, student_id, tenant_id, "
                        + "question, answer, status, on_topic, from_cache) "
                        + "VALUES (:pid, :c, :c, :sid, :stu, :tenant, :q, :a, :st, :on, false)",
                new MapSqlParameterSource("pid", UUID.randomUUID()).addValue("c", Timestamp.from(createdAt))
                        .addValue("sid", sessionId).addValue("stu", student).addValue("tenant", UUID.randomUUID())
                        .addValue("q", question).addValue("a", answer).addValue("st", status)
                        .addValue("on", onTopic));
    }

    public int messageRows(UUID sessionId) {
        return count("SELECT count(*) FROM chat_message WHERE session_id = :id",
                new MapSqlParameterSource("id", sessionId));
    }

    public int messageRowsForStudent(UUID student) {
        return count("SELECT count(*) FROM chat_message WHERE student_id = :s", new MapSqlParameterSource("s", student));
    }

    /** All rows of the session, oldest first, as column-name keyed maps. */
    public List<Map<String, Object>> messages(UUID sessionId) {
        return jdbc.queryForList("SELECT * FROM chat_message WHERE session_id = :id ORDER BY id",
                new MapSqlParameterSource("id", sessionId));
    }

    /** Every message row, oldest first; for tests where the session id itself may have been erased. */
    public List<Map<String, Object>> allMessages() {
        return jdbc.queryForList("SELECT * FROM chat_message ORDER BY id", new MapSqlParameterSource());
    }

    // ---- answer cache ------------------------------------------------------------------------------------

    public int cacheRows() {
        return count("SELECT count(*) FROM chat_answer_cache", new MapSqlParameterSource());
    }

    public int cacheRows(String cacheKey) {
        return count("SELECT count(*) FROM chat_answer_cache WHERE cache_key = :k",
                new MapSqlParameterSource("k", cacheKey));
    }

    public Map<String, Object> cacheRow(String cacheKey) {
        return jdbc.queryForMap("SELECT * FROM chat_answer_cache WHERE cache_key = :k",
                new MapSqlParameterSource("k", cacheKey));
    }

    public void insertCache(String cacheKey, String answer, Instant expiresAt) {
        jdbc.update("INSERT INTO chat_answer_cache (public_id, created_at, updated_at, cache_key, answer, "
                        + "prompt_version, model, expires_at) VALUES (:pid, now(), now(), :k, :a, 'v1', 'm', :e)",
                new MapSqlParameterSource("pid", UUID.randomUUID()).addValue("k", cacheKey).addValue("a", answer)
                        .addValue("e", expiresAt == null ? null : Timestamp.from(expiresAt)));
    }

    // ---- raw ---------------------------------------------------------------------------------------------

    public void execute(String sql) {
        jdbc.getJdbcOperations().execute(sql);
    }

    private int count(String sql, MapSqlParameterSource params) {
        Integer value = jdbc.queryForObject(sql, params, Integer.class);
        return value == null ? 0 : value;
    }
}
