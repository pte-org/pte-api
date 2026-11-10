package com.pte.chat.support;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Plain-SQL fixtures and probes for the chat Postgres tests; they never go through the code under test. */
public final class ChatTestData {

    private final NamedParameterJdbcTemplate jdbc;

    public ChatTestData(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void truncateAll() {
        jdbc.getJdbcOperations().execute("TRUNCATE chat_usage, chat_usage_daily RESTART IDENTITY");
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

    // ---- daily counters ----------------------------------------------------------------------------------

    /** Every daily row, oldest first, as column-name keyed maps. */
    public List<Map<String, Object>> dailyRows() {
        return jdbc.queryForList("SELECT * FROM chat_usage_daily ORDER BY usage_date, model", new MapSqlParameterSource());
    }

    /** The daily rows of one day summed over every model, for tests that do not care which model answered. */
    public Map<String, Object> dailyTotals(LocalDate date) {
        return jdbc.queryForMap("SELECT coalesce(sum(messages)::bigint, 0) AS messages, coalesce(sum(ok)::bigint, 0) AS ok, "
                        + "coalesce(sum(refused)::bigint, 0) AS refused, coalesce(sum(failed)::bigint, 0) AS failed, "
                        + "coalesce(sum(tokens_in)::bigint, 0) AS tokens_in, coalesce(sum(tokens_out)::bigint, 0) AS tokens_out, "
                        + "coalesce(sum(tokens_thinking)::bigint, 0) AS tokens_thinking FROM chat_usage_daily "
                        + "WHERE usage_date = :d", new MapSqlParameterSource("d", date));
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
