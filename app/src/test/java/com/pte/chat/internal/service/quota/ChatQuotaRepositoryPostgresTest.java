package com.pte.chat.internal.service.quota;

import com.pte.chat.support.ChatSharedPostgres;
import com.pte.chat.support.ChatTestData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The quota statement on a real Postgres. H2 and mocks cannot run {@code ON CONFLICT ... DO UPDATE ... WHERE},
 * so these tests are what proves NFR-04: the limit holds and a day has exactly one counter row, however many
 * requests arrive in the same instant.
 */
@Testcontainers(disabledWithoutDocker = true)
class ChatQuotaRepositoryPostgresTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 28);

    private static ChatQuotaRepository repository;
    private static ChatTestData data;

    @BeforeAll
    static void connect() {
        NamedParameterJdbcTemplate jdbc = new NamedParameterJdbcTemplate(ChatSharedPostgres.dataSource());
        repository = new ChatQuotaRepository(jdbc);
        data = new ChatTestData(jdbc);
    }

    /** Runs {@code threads} copies of the call released at the same instant and returns their results. */
    private static <T> List<T> concurrently(int threads, Callable<T> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.call();
                }));
            }
            ready.await();
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- sequential behaviour ----------------------------------------------------------------------------

    @Test
    void consumeCountsUpToTheLimitThenReturnsEmptyAndStaysThere() {
        UUID student = UUID.randomUUID();

        for (int expected = 1; expected <= 3; expected++) {
            assertThat(repository.consume(student, DAY, 3)).isEqualTo(OptionalInt.of(expected));
        }
        assertThat(repository.consume(student, DAY, 3)).isEmpty();
        assertThat(repository.consume(student, DAY, 3)).isEmpty();

        assertThat(repository.used(student, DAY)).isEqualTo(3);
        assertThat(data.usageRows(student, DAY)).isEqualTo(1);
    }

    @Test
    void usedIsZeroWhenThereIsNoRowAndReadingDoesNotCreateOne() {
        UUID student = UUID.randomUUID();

        assertThat(repository.used(student, DAY)).isZero();

        assertThat(data.usageRows(student)).isZero();
    }

    @Test
    void studentsAreCountedSeparately() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository.consume(first, DAY, 1);

        assertThat(repository.consume(first, DAY, 1)).isEmpty();
        assertThat(repository.consume(second, DAY, 1)).isEqualTo(OptionalInt.of(1));
    }

    @Test
    void daysAreCountedSeparately() {
        UUID student = UUID.randomUUID();
        LocalDate nextDay = DAY.plusDays(1);
        repository.consume(student, DAY, 1);

        assertThat(repository.consume(student, DAY, 1)).isEmpty();
        assertThat(repository.consume(student, nextDay, 1)).isEqualTo(OptionalInt.of(1));
        assertThat(data.usageRows(student)).isEqualTo(2);
    }

    @Test
    void aLimitRaisedLaterAllowsMoreMessagesTheSameDay() {
        UUID student = UUID.randomUUID();
        repository.consume(student, DAY, 1);
        assertThat(repository.consume(student, DAY, 1)).isEmpty();

        assertThat(repository.consume(student, DAY, 2)).isEqualTo(OptionalInt.of(2));
    }

    // ---- refund ------------------------------------------------------------------------------------------

    @Test
    void refundGivesOneMessageBackAndTheSlotCanBeUsedAgain() {
        UUID student = UUID.randomUUID();
        repository.consume(student, DAY, 1);
        assertThat(repository.consume(student, DAY, 1)).isEmpty();

        repository.refund(student, DAY);

        assertThat(repository.used(student, DAY)).isZero();
        assertThat(repository.consume(student, DAY, 1)).isEqualTo(OptionalInt.of(1));
    }

    @Test
    void refundNeverGoesBelowZero() {
        UUID student = UUID.randomUUID();
        repository.consume(student, DAY, 5);

        repository.refund(student, DAY);
        repository.refund(student, DAY);
        repository.refund(student, DAY);

        assertThat(repository.used(student, DAY)).isZero();
    }

    @Test
    void refundWithoutAnyRowDoesNothingAndCreatesNoRow() {
        UUID student = UUID.randomUUID();

        repository.refund(student, DAY);

        assertThat(repository.used(student, DAY)).isZero();
        assertThat(data.usageRows(student)).isZero();
    }

    @Test
    void refundTouchesOnlyTheDayItWasGiven() {
        UUID student = UUID.randomUUID();
        LocalDate nextDay = DAY.plusDays(1);
        repository.consume(student, DAY, 5);
        repository.consume(student, DAY, 5);
        repository.consume(student, nextDay, 5);

        repository.refund(student, DAY);

        assertThat(repository.used(student, DAY)).isEqualTo(1);
        assertThat(repository.used(student, nextDay)).isEqualTo(1);
    }

    @Test
    void refundTouchesOnlyTheGivenStudent() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        repository.consume(first, DAY, 5);
        repository.consume(second, DAY, 5);

        repository.refund(first, DAY);

        assertThat(repository.used(first, DAY)).isZero();
        assertThat(repository.used(second, DAY)).isEqualTo(1);
    }

    // ---- concurrency -------------------------------------------------------------------------------------

    @Test
    void fiftyThreadsOnAFreshDayCreateExactlyOneRowAndCountEveryRequest() throws Exception {
        UUID student = UUID.randomUUID();

        List<OptionalInt> results = concurrently(50, () -> repository.consume(student, DAY, 1_000));

        assertThat(results).allMatch(OptionalInt::isPresent);
        assertThat(results.stream().map(OptionalInt::getAsInt).collect(Collectors.toSet()))
                .isEqualTo(IntStream.rangeClosed(1, 50).boxed().collect(Collectors.toSet()));
        assertThat(data.usageRows(student, DAY)).isEqualTo(1);
        assertThat(data.used(student, DAY)).isEqualTo(50);
    }

    @RepeatedTest(20)
    void limitOneWithTwoThreadsLetsExactlyOneThrough() throws Exception {
        UUID student = UUID.randomUUID();

        List<OptionalInt> results = concurrently(2, () -> repository.consume(student, DAY, 1));

        assertThat(results.stream().filter(OptionalInt::isPresent).count()).isEqualTo(1);
        assertThat(results.stream().filter(OptionalInt::isEmpty).count()).isEqualTo(1);
        assertThat(data.usageRows(student, DAY)).isEqualTo(1);
        assertThat(data.used(student, DAY)).isEqualTo(1);
    }

    @RepeatedTest(5)
    void limitTwentyWithFiftyThreadsLetsExactlyTwentyThrough() throws Exception {
        UUID student = UUID.randomUUID();

        List<OptionalInt> results = concurrently(50, () -> repository.consume(student, DAY, 20));

        List<Integer> granted = results.stream().filter(OptionalInt::isPresent).map(OptionalInt::getAsInt).toList();
        assertThat(granted).hasSize(20);
        assertThat(granted).containsExactlyInAnyOrderElementsOf(
                IntStream.rangeClosed(1, 20).boxed().collect(Collectors.toList()));
        assertThat(results.stream().filter(OptionalInt::isEmpty).count()).isEqualTo(30);
        assertThat(data.used(student, DAY)).isEqualTo(20);
        assertThat(data.usageRows(student, DAY)).isEqualTo(1);
    }

    @Test
    void concurrentRefundsNeverDriveTheCounterNegative() throws Exception {
        UUID student = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            repository.consume(student, DAY, 100);
        }

        concurrently(30, () -> {
            repository.refund(student, DAY);
            return null;
        });

        assertThat(data.used(student, DAY)).isZero();
    }
}
