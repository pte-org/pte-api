package com.pte.chat.internal.service.quota;

import com.pte.chat.internal.constant.ChatConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * The daily counter, changed only by single statements. The upsert is what makes the quota safe under
 * concurrency: the first message of a day inserts the row, later ones update it, and the WHERE clause refuses
 * the update once the limit is reached, so the database returns no row and the caller reads that as "exhausted".
 * Each call runs in its own short transaction and is never held open across the Gemini call.
 */
@Repository
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatQuotaRepository {

    private static final String CONSUME = "INSERT INTO chat_usage AS u (student_id, usage_date, used) "
            + "VALUES (:sid, :date, 1) ON CONFLICT (student_id, usage_date) DO UPDATE SET used = u.used + 1 "
            + "WHERE u.used < :limit RETURNING u.used";

    private final NamedParameterJdbcTemplate jdbc;

    public ChatQuotaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public OptionalInt consume(UUID studentId, LocalDate usageDate, int limit) {
        return jdbc.query(CONSUME, new MapSqlParameterSource("sid", studentId).addValue("date", usageDate)
                .addValue("limit", limit), rs -> rs.next() ? OptionalInt.of(rs.getInt(1)) : OptionalInt.empty());
    }

    public void refund(UUID studentId, LocalDate usageDate) {
        jdbc.update("UPDATE chat_usage SET used = GREATEST(used - 1, 0) WHERE student_id = :sid AND usage_date = :date",
                new MapSqlParameterSource("sid", studentId).addValue("date", usageDate));
    }

    public int used(UUID studentId, LocalDate usageDate) {
        return jdbc.query("SELECT used FROM chat_usage WHERE student_id = :sid AND usage_date = :date",
                new MapSqlParameterSource("sid", studentId).addValue("date", usageDate),
                rs -> rs.next() ? rs.getInt(1) : 0);
    }
}
