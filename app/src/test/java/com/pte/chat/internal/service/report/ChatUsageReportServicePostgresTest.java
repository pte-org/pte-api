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
import java.util.UUID;

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

    private static void used(UUID student, LocalDate day, int count) {
        jdbc.getJdbcOperations().update("INSERT INTO chat_usage (student_id, usage_date, used) VALUES (?, ?, ?)",
                student, java.sql.Date.valueOf(day), count);
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
    void aRangeThatEndsBeforeItStartsOrIsLongerThanAYearIsRejected() {
        assertThatThrownBy(() -> service.report(D30, D28)).isInstanceOf(ChatInvalidRequestException.class);
        assertThatThrownBy(() -> service.report(D28, D28.plusDays(400))).isInstanceOf(ChatInvalidRequestException.class);
    }

    @Test
    void theBusiestStudentsComeFirstWithTheirMessageCountsOverTheRange() {
        UUID busy = UUID.randomUUID();
        UUID quiet = UUID.randomUUID();
        UUID outside = UUID.randomUUID();
        used(busy, D28, 20);
        used(busy, D29, 15);
        used(quiet, D29, 3);
        used(outside, D30.plusDays(5), 99);

        List<ChatStudentUsage> top = service.topStudents(D28, D30, 10);

        assertThat(top).containsExactly(new ChatStudentUsage(busy, 35), new ChatStudentUsage(quiet, 3));
    }

    @Test
    void theListIsCutAtTheRequestedSizeAndTheSizeIsBounded() {
        for (int i = 0; i < 5; i++) {
            used(UUID.randomUUID(), D29, i + 1);
        }

        assertThat(service.topStudents(D28, D30, 2)).hasSize(2);
        assertThat(service.topStudents(D28, D30, 0)).hasSize(1);
        assertThat(service.topStudents(D28, D30, 100_000)).hasSize(5);
    }

    @Test
    void studentsWithNothingUsedAreNotListed() {
        used(UUID.randomUUID(), D29, 0);

        assertThat(service.topStudents(D28, D30, 10)).isEmpty();
    }
}
