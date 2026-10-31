package com.pte.chat.internal.service.report;

import com.pte.chat.internal.exception.ChatInvalidRequestException;
import com.pte.chat.support.ChatSharedPostgres;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The admin's view of chat cost and traffic by Vietnam day, read from the content-free daily table. */
@Testcontainers(disabledWithoutDocker = true)
class ChatUsageReportServicePostgresTest {

    private static final LocalDate D28 = LocalDate.of(2026, 10, 28);
    private static final LocalDate D29 = LocalDate.of(2026, 10, 29);
    private static final LocalDate D30 = LocalDate.of(2026, 10, 30);

    private static NamedParameterJdbcTemplate jdbc;
    private static ChatUsageReportService service;

    @BeforeAll
    static void connect() {
        jdbc = new NamedParameterJdbcTemplate(ChatSharedPostgres.dataSource());
        service = new ChatUsageReportService(jdbc);
    }

    @BeforeEach
    void clean() {
        jdbc.getJdbcOperations().execute("TRUNCATE chat_usage_daily, chat_usage");
    }

    private static void daily(LocalDate day, String model, int ok, int refused, int failed, long in, long out,
                              long thinking, String cost) {
        jdbc.getJdbcOperations().update("INSERT INTO chat_usage_daily (usage_date, model, messages, ok, refused, failed, "
                        + "tokens_in, tokens_out, tokens_thinking, cost_usd) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::numeric)",
                java.sql.Date.valueOf(day), model, ok + refused + failed, ok, refused, failed, in, out, thinking, cost);
    }

    private static ChatUsageDay day(List<ChatUsageDay> report, LocalDate date) {
        return report.stream().filter(d -> d.date().equals(date)).findFirst().orElseThrow();
    }

    @Test
    void everyDayInTheRangeIsListedEvenWithoutTraffic() {
        List<ChatUsageDay> report = service.report(D28, D30);

        assertThat(report).extracting(ChatUsageDay::date).containsExactly(D28, D29, D30);
        assertThat(report).allSatisfy(d -> assertThat(d.messages()).isZero());
    }

    @Test
    void aDayAddsUpItsModelsAndTheirCost() {
        daily(D29, "gemini-2.5-flash-lite", 3, 1, 0, 1000, 200, 50, "0.000254");
        daily(D29, "gemini-3.5-flash-lite", 2, 0, 1, 500, 100, 0, "0.000100");

        ChatUsageDay report = day(service.report(D28, D30), D29);

        assertThat(report.messages()).isEqualTo(7);
        assertThat(report.ok()).isEqualTo(5);
        assertThat(report.refused()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.tokensIn()).isEqualTo(1500);
        assertThat(report.tokensOut()).isEqualTo(300);
        assertThat(report.tokensThinking()).isEqualTo(50);
        assertThat(report.estimatedCostUsd()).isEqualByComparingTo("0.0004");
    }

    @Test
    void daysOutsideTheRangeAreLeftOut() {
        daily(D28.minusDays(1), "m", 5, 0, 0, 1, 1, 0, "0.1");
        daily(D30.plusDays(1), "m", 5, 0, 0, 1, 1, 0, "0.1");

        assertThat(service.report(D28, D30)).allSatisfy(d -> assertThat(d.messages()).isZero());
    }

    @Test
    void theFailuresOfADayAreSplitByReasonAndSummedOverModels() {
        jdbc.getJdbcOperations().update("INSERT INTO chat_usage_daily (usage_date, model, messages, failed, "
                + "failed_timeout, failed_rate_limited, failed_other) VALUES (?, 'a', 3, 3, 2, 1, 0), (?, 'b', 2, 2, 1, 0, 1)",
                java.sql.Date.valueOf(D29), java.sql.Date.valueOf(D29));

        ChatUsageDay report = day(service.report(D28, D30), D29);

        assertThat(report.failed()).isEqualTo(5);
        assertThat(report.failures()).isEqualTo(new ChatFailureCounts(3, 1, 0, 0, 0, 1));
        assertThat(day(service.report(D28, D30), D28).failures()).isEqualTo(ChatFailureCounts.NONE);
    }

    @Test
    void aRangeThatEndsBeforeItStartsOrIsLongerThanAYearIsRejected() {
        assertThatThrownBy(() -> service.report(D30, D28)).isInstanceOf(ChatInvalidRequestException.class);
        assertThatThrownBy(() -> service.report(D28, D28.plusDays(400))).isInstanceOf(ChatInvalidRequestException.class);
    }
}
