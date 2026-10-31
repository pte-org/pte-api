package com.pte.chat.internal.service.report;

import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.exception.ChatInvalidRequestException;
import com.pte.chat.support.ChatPostgresJpaTest;
import com.pte.chat.support.ChatTestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The PM's view of chat cost and traffic by Vietnam day, built from the message rows and nothing personal. */
@ChatPostgresJpaTest
class ChatUsageReportServicePostgresTest {

    private static final LocalDate D28 = LocalDate.of(2026, 10, 28);
    private static final LocalDate D29 = LocalDate.of(2026, 10, 29);
    private static final LocalDate D30 = LocalDate.of(2026, 10, 30);

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private ChatAiProperties properties;
    private ChatUsageReportService service;
    private ChatTestData data;
    private UUID student;
    private UUID session;

    @BeforeEach
    void setUp() {
        properties = new ChatAiProperties();
        service = new ChatUsageReportService(jdbc, properties);
        data = new ChatTestData(jdbc);
        data.truncateAll();
        student = UUID.randomUUID();
        session = UUID.randomUUID();
    }

    private void message(String question, String status, Instant at, int in, int out, int thinking, boolean cache) {
        data.insertMessage(session, student, question, "a", status, at);
        data.execute("UPDATE chat_message SET tokens_in = " + in + ", tokens_out = " + out + ", tokens_thinking = "
                + thinking + ", from_cache = " + cache + " WHERE question = '" + question + "'");
    }

    private ChatUsageDay day(List<ChatUsageDay> report, LocalDate date) {
        return report.stream().filter(d -> d.date().equals(date)).findFirst().orElseThrow();
    }

    @Test
    void everyDayInTheRangeIsListedEvenWithoutTraffic() {
        List<ChatUsageDay> report = service.report(D28, D30);

        assertThat(report).extracting(ChatUsageDay::date).containsExactly(D28, D29, D30);
        assertThat(report).allSatisfy(d -> assertThat(d.messages()).isZero());
    }

    @Test
    void messagesAreCountedByStatusAndTokensAreSummed() {
        message("q1", "OK", Instant.parse("2026-10-29T03:00:00Z"), 100, 200, 0, false);
        message("q2", "OK", Instant.parse("2026-10-29T04:00:00Z"), 50, 60, 10, false);
        message("q3", "REFUSED", Instant.parse("2026-10-29T05:00:00Z"), 30, 5, 0, false);
        message("q4", "FAILED", Instant.parse("2026-10-29T06:00:00Z"), 20, 0, 0, false);

        ChatUsageDay d = day(service.report(D29, D29), D29);

        assertThat(d.ok()).isEqualTo(2);
        assertThat(d.refused()).isEqualTo(1);
        assertThat(d.failed()).isEqualTo(1);
        assertThat(d.messages()).isEqualTo(4);
        assertThat(d.tokensIn()).isEqualTo(200);
        assertThat(d.tokensOut()).isEqualTo(265);
        assertThat(d.tokensThinking()).isEqualTo(10);
    }

    @Test
    void answersServedFromTheCacheAreCountedSeparately() {
        message("q1", "OK", Instant.parse("2026-10-29T03:00:00Z"), 100, 200, 0, false);
        message("q2", "OK", Instant.parse("2026-10-29T03:01:00Z"), 0, 0, 0, true);

        assertThat(day(service.report(D29, D29), D29).cacheHits()).isEqualTo(1);
    }

    @Test
    void theDayIsTheVietnamDayNotTheUtcDay() {
        // 16:59 UTC is 23:59 on the 29th in Vietnam; 17:00 UTC is 00:00 on the 30th.
        message("late", "OK", Instant.parse("2026-10-29T16:59:00Z"), 1, 1, 0, false);
        message("early", "OK", Instant.parse("2026-10-29T17:00:00Z"), 1, 1, 0, false);

        List<ChatUsageDay> report = service.report(D29, D30);

        assertThat(day(report, D29).messages()).isEqualTo(1);
        assertThat(day(report, D30).messages()).isEqualTo(1);
    }

    @Test
    void rowsOutsideTheRangeAreIgnored() {
        message("old", "OK", Instant.parse("2026-10-27T03:00:00Z"), 1, 1, 0, false);

        assertThat(service.report(D29, D29)).singleElement().satisfies(d -> assertThat(d.messages()).isZero());
    }

    @Test
    void anonymisedRowsStillCountBecauseTheirTokensAreKept() {
        message("q1", "OK", Instant.parse("2026-10-29T03:00:00Z"), 100, 200, 0, false);
        data.execute("UPDATE chat_message SET question = '', answer = '', student_id = '00000000-0000-0000-0000-000000000000'");

        assertThat(day(service.report(D29, D29), D29).tokensIn()).isEqualTo(100);
    }

    @Test
    void theCostFollowsTheConfiguredPricesPerMillionTokens() {
        properties.setPriceInputPerMillionUsd(new BigDecimal("0.10"));
        properties.setPriceOutputPerMillionUsd(new BigDecimal("0.40"));
        message("q1", "OK", Instant.parse("2026-10-29T03:00:00Z"), 1_000_000, 500_000, 500_000, false);

        // 1M in at 0.10 + (0.5M out + 0.5M thinking) at 0.40
        assertThat(day(service.report(D29, D29), D29).estimatedCostUsd()).isEqualByComparingTo("0.5000");
    }

    @Test
    void theCostIsZeroForADayWithNoTraffic() {
        assertThat(day(service.report(D29, D29), D29).estimatedCostUsd()).isEqualByComparingTo("0");
    }

    @Test
    void aRangeThatEndsBeforeItStartsIsRejected() {
        assertThatThrownBy(() -> service.report(D30, D28)).isInstanceOf(ChatInvalidRequestException.class);
    }

    @Test
    void aRangeLongerThanAYearIsRejected() {
        assertThatThrownBy(() -> service.report(D29, D29.plusDays(366))).isInstanceOf(ChatInvalidRequestException.class);
        assertThat(service.report(D29, D29.plusDays(365))).hasSize(366);
    }

    @Test
    void theReportCarriesNothingAboutAnyStudent() {
        message("q1", "OK", Instant.parse("2026-10-29T03:00:00Z"), 1, 1, 0, false);

        assertThat(ChatUsageDay.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .noneMatch(name -> name.toLowerCase().contains("student") || name.toLowerCase().contains("question")
                        || name.toLowerCase().contains("answer") || name.toLowerCase().contains("session"));
    }
}
