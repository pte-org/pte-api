package com.pte.chat.internal.service.report;

import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.exception.ChatInvalidRequestException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat traffic and an estimated cost for each Vietnam day, for the people who watch the budget. The estimate is
 * tokens times the configured prices; the invoice from the provider is the real figure.
 */
@Service
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatUsageReportService {

    private static final long MAX_RANGE_DAYS = 366;
    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);
    private static final int COST_SCALE = 4;

    private static final String SQL = "SELECT (created_at AT TIME ZONE '" + ChatConstants.ZONE_ID + "')::date AS day, "
            + "count(*) AS messages, "
            + "count(*) FILTER (WHERE status = '" + ChatConstants.STATUS_OK + "') AS ok, "
            + "count(*) FILTER (WHERE status = '" + ChatConstants.STATUS_REFUSED + "') AS refused, "
            + "count(*) FILTER (WHERE status = '" + ChatConstants.STATUS_FAILED + "') AS failed, "
            + "count(*) FILTER (WHERE from_cache) AS cache_hits, "
            + "coalesce(sum(tokens_in), 0) AS tokens_in, coalesce(sum(tokens_out), 0) AS tokens_out, "
            + "coalesce(sum(tokens_thinking), 0) AS tokens_thinking "
            + "FROM chat_message WHERE deleted = false "
            + "AND (created_at AT TIME ZONE '" + ChatConstants.ZONE_ID + "')::date BETWEEN :from AND :to GROUP BY day";

    private final NamedParameterJdbcTemplate jdbc;
    private final ChatAiProperties properties;

    public ChatUsageReportService(NamedParameterJdbcTemplate jdbc, ChatAiProperties properties) {
        this.jdbc = jdbc;
        this.properties = properties;
    }

    /** One entry per day from {@code from} to {@code to} inclusive; days without traffic are all zeros. */
    public List<ChatUsageDay> report(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new ChatInvalidRequestException(ChatConstants.DETAIL_REPORT_RANGE_INVALID);
        }
        Map<LocalDate, ChatUsageDay> byDay = new HashMap<>();
        jdbc.query(SQL, new MapSqlParameterSource("from", Date.valueOf(from)).addValue("to", Date.valueOf(to)),
                (rs, n) -> {
                    LocalDate day = rs.getDate("day").toLocalDate();
                    long in = rs.getLong("tokens_in");
                    long out = rs.getLong("tokens_out");
                    long thinking = rs.getLong("tokens_thinking");
                    byDay.put(day, new ChatUsageDay(day, rs.getInt("messages"), rs.getInt("ok"), rs.getInt("refused"),
                            rs.getInt("failed"), rs.getInt("cache_hits"), in, out, thinking, cost(in, out + thinking)));
                    return day;
                });
        List<ChatUsageDay> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            days.add(byDay.getOrDefault(day, new ChatUsageDay(day, 0, 0, 0, 0, 0, 0, 0, 0, cost(0, 0))));
        }
        return days;
    }

    /** Thinking tokens are billed as output. */
    private BigDecimal cost(long input, long output) {
        BigDecimal in = properties.getPriceInputPerMillionUsd().multiply(BigDecimal.valueOf(input));
        BigDecimal out = properties.getPriceOutputPerMillionUsd().multiply(BigDecimal.valueOf(output));
        return in.add(out).divide(ONE_MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }
}
