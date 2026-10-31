package com.pte.chat.internal.service.report;

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
import java.util.UUID;

/**
 * Chat traffic and an estimated cost for each Vietnam day, and the students who sent the most, for the people
 * who watch the budget. Both come from counters kept by the chat itself (the daily table and the quota
 * counter): there is no stored conversation to read. The cost is the estimate made when each message was
 * counted; the invoice from the provider is the real figure.
 */
@Service
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatUsageReportService {

    private static final long MAX_RANGE_DAYS = 366;
    private static final int MAX_STUDENTS = 100;
    private static final int COST_SCALE = 4;

    private static final String DAYS_SQL = "SELECT usage_date AS day, sum(messages) AS messages, sum(ok) AS ok, "
            + "sum(refused) AS refused, sum(failed) AS failed, sum(tokens_in) AS tokens_in, "
            + "sum(tokens_out) AS tokens_out, sum(tokens_thinking) AS tokens_thinking, sum(cost_usd) AS cost "
            + "FROM chat_usage_daily WHERE usage_date BETWEEN :from AND :to GROUP BY usage_date";

    private static final String STUDENTS_SQL = "SELECT student_id, sum(used) AS messages FROM chat_usage "
            + "WHERE usage_date BETWEEN :from AND :to GROUP BY student_id HAVING sum(used) > 0 "
            + "ORDER BY messages DESC, student_id LIMIT :limit";

    private final NamedParameterJdbcTemplate jdbc;

    public ChatUsageReportService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** One entry per day from {@code from} to {@code to} inclusive; days without traffic are all zeros. */
    public List<ChatUsageDay> report(LocalDate from, LocalDate to) {
        requireRange(from, to);
        Map<LocalDate, ChatUsageDay> byDay = new HashMap<>();
        jdbc.query(DAYS_SQL, range(from, to), (rs, n) -> {
            LocalDate day = rs.getDate("day").toLocalDate();
            byDay.put(day, new ChatUsageDay(day, rs.getInt("messages"), rs.getInt("ok"), rs.getInt("refused"),
                    rs.getInt("failed"), rs.getLong("tokens_in"), rs.getLong("tokens_out"),
                    rs.getLong("tokens_thinking"), rs.getBigDecimal("cost").setScale(COST_SCALE, RoundingMode.HALF_UP)));
            return day;
        });
        List<ChatUsageDay> days = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            days.add(byDay.getOrDefault(day, new ChatUsageDay(day, 0, 0, 0, 0, 0, 0, 0,
                    BigDecimal.ZERO.setScale(COST_SCALE, RoundingMode.UNNECESSARY))));
        }
        return days;
    }

    /** The students with the most messages in the range, busiest first; {@code limit} is kept between 1 and 100. */
    public List<ChatStudentUsage> topStudents(LocalDate from, LocalDate to, int limit) {
        requireRange(from, to);
        int bounded = Math.min(Math.max(limit, 1), MAX_STUDENTS);
        return jdbc.query(STUDENTS_SQL, range(from, to).addValue("limit", bounded),
                (rs, n) -> new ChatStudentUsage(rs.getObject("student_id", UUID.class), rs.getInt("messages")));
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_RANGE_DAYS) {
            throw new ChatInvalidRequestException(ChatConstants.DETAIL_REPORT_RANGE_INVALID);
        }
    }

    private static MapSqlParameterSource range(LocalDate from, LocalDate to) {
        return new MapSqlParameterSource("from", Date.valueOf(from)).addValue("to", Date.valueOf(to));
    }
}
