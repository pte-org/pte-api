package com.pte.chat.internal.service.usage;

import com.pte.chat.internal.client.ChatAiUsage;
import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.support.ChatSharedPostgres;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** The daily counters on a real Postgres: one row per day and model, and no count is lost under concurrency. */
@Testcontainers(disabledWithoutDocker = true)
class ChatUsageDailyRepositoryPostgresTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 28);
    private static final String MODEL = "gemini-3.5-flash-lite";

    private static NamedParameterJdbcTemplate jdbc;
    private static ChatUsageDailyRepository repository;

    @BeforeAll
    static void connect() {
        jdbc = new NamedParameterJdbcTemplate(ChatSharedPostgres.dataSource());
        repository = new ChatUsageDailyRepository(jdbc);
    }

    @BeforeEach
    void clean() {
        jdbc.getJdbcOperations().execute("TRUNCATE chat_usage_daily");
    }

    private static List<Map<String, Object>> rows() {
        return jdbc.getJdbcOperations().queryForList("SELECT * FROM chat_usage_daily ORDER BY usage_date, model");
    }

    @Test
    void theFirstMessageOfADayCreatesTheRow() {
        repository.add(DAY, MODEL, ChatConstants.STATUS_OK, new ChatAiUsage(100, 20, 5, "STOP"),
                new BigDecimal("0.000012"));

        assertThat(rows()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("model", MODEL).containsEntry("messages", 1).containsEntry("ok", 1)
                    .containsEntry("refused", 0).containsEntry("failed", 0).containsEntry("tokens_in", 100L)
                    .containsEntry("tokens_out", 20L).containsEntry("tokens_thinking", 5L);
            assertThat((BigDecimal) row.get("cost_usd")).isEqualByComparingTo("0.000012");
        });
    }

    @Test
    void laterMessagesOfTheSameDayAndModelAddUp() {
        repository.add(DAY, MODEL, ChatConstants.STATUS_OK, new ChatAiUsage(100, 20, 5, "STOP"), new BigDecimal("0.000010"));
        repository.add(DAY, MODEL, ChatConstants.STATUS_REFUSED, new ChatAiUsage(50, 2, 0, "STOP"), new BigDecimal("0.000005"));
        repository.add(DAY, MODEL, ChatConstants.STATUS_FAILED, ChatAiUsage.NONE, BigDecimal.ZERO);

        assertThat(rows()).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("messages", 3).containsEntry("ok", 1).containsEntry("refused", 1)
                    .containsEntry("failed", 1).containsEntry("tokens_in", 150L).containsEntry("tokens_out", 22L)
                    .containsEntry("tokens_thinking", 5L);
            assertThat((BigDecimal) row.get("cost_usd")).isEqualByComparingTo("0.000015");
        });
    }

    @Test
    void anotherModelOrDayIsItsOwnRow() {
        repository.add(DAY, MODEL, ChatConstants.STATUS_OK, ChatAiUsage.NONE, BigDecimal.ZERO);
        repository.add(DAY, "gemini-2.5-flash-lite", ChatConstants.STATUS_OK, ChatAiUsage.NONE, BigDecimal.ZERO);
        repository.add(DAY.plusDays(1), MODEL, ChatConstants.STATUS_OK, ChatAiUsage.NONE, BigDecimal.ZERO);

        assertThat(rows()).hasSize(3);
    }

    @Test
    void noCountIsLostWhenManyMessagesArriveTogether() throws Exception {
        int threads = 30;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    repository.add(DAY, MODEL, ChatConstants.STATUS_OK, new ChatAiUsage(10, 1, 0, "STOP"),
                            new BigDecimal("0.000001"));
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(rows()).singleElement().satisfies(row ->
                assertThat(row).containsEntry("messages", threads).containsEntry("tokens_in", 10L * threads));
    }
}
