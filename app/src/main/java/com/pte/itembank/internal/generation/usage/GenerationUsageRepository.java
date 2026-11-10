package com.pte.itembank.internal.generation.usage;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

/** Daily token counters of question generation, added to by one statement so concurrent calls never lose a count. */
@Repository
public class GenerationUsageRepository {

    private static final String ADD = "INSERT INTO generation_usage_daily AS d (usage_date, role, model, calls, "
            + "tokens_in, tokens_out) VALUES (:day, :role, :model, :calls, :tin, :tout) "
            + "ON CONFLICT (usage_date, role, model) DO UPDATE SET calls = d.calls + :calls, "
            + "tokens_in = d.tokens_in + :tin, tokens_out = d.tokens_out + :tout";
    private static final String SUM = "SELECT coalesce(sum(calls), 0) AS calls, coalesce(sum(tokens_in), 0) AS tin, "
            + "coalesce(sum(tokens_out), 0) AS tout FROM generation_usage_daily WHERE usage_date BETWEEN :from AND :to";

    private final NamedParameterJdbcTemplate jdbc;

    public GenerationUsageRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void add(LocalDate day, String role, String model, GenerationUsage usage) {
        jdbc.update(ADD, new MapSqlParameterSource("day", day).addValue("role", role).addValue("model", model)
                .addValue("calls", usage.calls()).addValue("tin", usage.tokensIn()).addValue("tout", usage.tokensOut()));
    }

    /** Totals from {@code from} to {@code to}, both days included. */
    public GenerationUsage sum(LocalDate from, LocalDate to) {
        return jdbc.queryForObject(SUM, new MapSqlParameterSource("from", from).addValue("to", to),
                (row, index) -> new GenerationUsage(row.getLong("calls"), row.getLong("tin"), row.getLong("tout")));
    }
}
