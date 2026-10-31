package com.pte.chat.internal.service.usage;

import com.pte.chat.internal.client.ChatAiUsage;
import com.pte.chat.internal.constant.ChatConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The daily counters behind the admin cost screen: one row per Vietnam day and model, added to by one
 * statement, so two messages in the same instant never lose a count. A row has numbers only: no student, no text.
 */
@Repository
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatUsageDailyRepository {

    private static final String ADD = "INSERT INTO chat_usage_daily AS d (usage_date, model, messages, ok, refused, "
            + "failed, tokens_in, tokens_out, tokens_thinking, cost_usd) "
            + "VALUES (:day, :model, 1, :ok, :refused, :failed, :tin, :tout, :tthink, :cost) "
            + "ON CONFLICT (usage_date, model) DO UPDATE SET messages = d.messages + 1, ok = d.ok + :ok, "
            + "refused = d.refused + :refused, failed = d.failed + :failed, tokens_in = d.tokens_in + :tin, "
            + "tokens_out = d.tokens_out + :tout, tokens_thinking = d.tokens_thinking + :tthink, "
            + "cost_usd = d.cost_usd + :cost";

    private final NamedParameterJdbcTemplate jdbc;

    public ChatUsageDailyRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Counts one message with the given outcome ({@code STATUS_OK}, {@code STATUS_REFUSED} or {@code STATUS_FAILED}). */
    public void add(LocalDate day, String model, String status, ChatAiUsage usage, BigDecimal cost) {
        jdbc.update(ADD, new MapSqlParameterSource("day", day).addValue("model", model)
                .addValue("ok", flag(ChatConstants.STATUS_OK, status))
                .addValue("refused", flag(ChatConstants.STATUS_REFUSED, status))
                .addValue("failed", flag(ChatConstants.STATUS_FAILED, status))
                .addValue("tin", usage.tokensIn()).addValue("tout", usage.tokensOut())
                .addValue("tthink", usage.tokensThinking()).addValue("cost", cost));
    }

    private static int flag(String expected, String actual) {
        return expected.equals(actual) ? 1 : 0;
    }
}
