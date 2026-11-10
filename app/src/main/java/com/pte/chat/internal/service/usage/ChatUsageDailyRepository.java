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
            + "failed, failed_timeout, failed_rate_limited, failed_auth, failed_unavailable, failed_truncated, "
            + "failed_other, tokens_in, tokens_out, tokens_thinking, cost_usd) "
            + "VALUES (:day, :model, 1, :ok, :refused, :failed, :f_timeout, :f_rate, :f_auth, :f_unavailable, "
            + ":f_truncated, :f_other, :tin, :tout, :tthink, :cost) "
            + "ON CONFLICT (usage_date, model) DO UPDATE SET messages = d.messages + 1, ok = d.ok + :ok, "
            + "refused = d.refused + :refused, failed = d.failed + :failed, "
            + "failed_timeout = d.failed_timeout + :f_timeout, failed_rate_limited = d.failed_rate_limited + :f_rate, "
            + "failed_auth = d.failed_auth + :f_auth, failed_unavailable = d.failed_unavailable + :f_unavailable, "
            + "failed_truncated = d.failed_truncated + :f_truncated, failed_other = d.failed_other + :f_other, "
            + "tokens_in = d.tokens_in + :tin, tokens_out = d.tokens_out + :tout, "
            + "tokens_thinking = d.tokens_thinking + :tthink, cost_usd = d.cost_usd + :cost";

    private final NamedParameterJdbcTemplate jdbc;

    public ChatUsageDailyRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Counts one message with the given outcome ({@code STATUS_OK}, {@code STATUS_REFUSED} or {@code STATUS_FAILED}).
     * A failure counted this way has no known reason and goes under {@link ChatFailureBucket#OTHER}.
     */
    public void add(LocalDate day, String model, String status, ChatAiUsage usage, BigDecimal cost) {
        add(day, model, status, ChatFailureBucket.OTHER, usage, cost);
    }

    /** As above; {@code bucket} says why the message failed and is ignored unless the status is FAILED. */
    public void add(LocalDate day, String model, String status, ChatFailureBucket bucket, ChatAiUsage usage,
                    BigDecimal cost) {
        boolean failed = ChatConstants.STATUS_FAILED.equals(status);
        jdbc.update(ADD, new MapSqlParameterSource("day", day).addValue("model", model)
                .addValue("ok", flag(ChatConstants.STATUS_OK.equals(status)))
                .addValue("refused", flag(ChatConstants.STATUS_REFUSED.equals(status)))
                .addValue("failed", flag(failed))
                .addValue("f_timeout", flag(failed && bucket == ChatFailureBucket.TIMEOUT))
                .addValue("f_rate", flag(failed && bucket == ChatFailureBucket.RATE_LIMITED))
                .addValue("f_auth", flag(failed && bucket == ChatFailureBucket.AUTH))
                .addValue("f_unavailable", flag(failed && bucket == ChatFailureBucket.UNAVAILABLE))
                .addValue("f_truncated", flag(failed && bucket == ChatFailureBucket.TRUNCATED))
                .addValue("f_other", flag(failed && bucket == ChatFailureBucket.OTHER))
                .addValue("tin", usage.tokensIn()).addValue("tout", usage.tokensOut())
                .addValue("tthink", usage.tokensThinking()).addValue("cost", cost));
    }

    private static int flag(boolean condition) {
        return condition ? 1 : 0;
    }
}
