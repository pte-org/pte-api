package com.pte.chat.internal.service;

import com.pte.chat.internal.exception.ChatBusyException;
import com.pte.chat.internal.exception.ChatQuotaExceededException;
import com.pte.chat.internal.exception.ChatRequestInFlightException;
import com.pte.chat.support.ChatPostgresJpaTest;
import com.pte.chat.support.GeminiResponses;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Time and overlap: one request per student at a time, a global cap on provider calls, and a day boundary that
 * passes while the provider is still thinking. A slow fake response keeps the first request in flight.
 */
@ChatPostgresJpaTest
class ChatServiceConcurrencyPostgresTest extends ChatFlowPostgresBase {

    private static final LocalDate NEXT_DAY = LocalDate.of(2026, 10, 29);
    private static final Instant DAY_RESET = Instant.parse("2026-10-28T17:00:00Z");
    private static final Instant PAST_MIDNIGHT = Instant.parse("2026-10-28T17:00:01Z");

    private final ExecutorService pool = Executors.newCachedThreadPool();

    @org.junit.jupiter.api.AfterEach
    void stopThreads() {
        pool.shutdownNow();
    }

    private static <T> T result(Future<T> future) throws Exception {
        try {
            return future.get();
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw ex;
        }
    }

    // ---- one request per student ---------------------------------------------------------------------------

    @Test
    void aSecondRequestWhileTheFirstIsRunningIsRejectedWithoutTouchingTheQuota() throws Exception {
        ChatFlowHarness h = start();
        h.server.enqueueDelayed(1_200, 200, GeminiResponses.onTopic("slow answer"));
        Future<ChatReply> first = pool.submit(() -> h.send(alice, session, "slow question"));
        h.awaitProviderRequests(1);
        UUID otherSession = UUID.randomUUID();

        assertThatThrownBy(() -> h.send(alice, otherSession, "impatient question"))
                .isInstanceOf(ChatRequestInFlightException.class);

        assertThat(result(first).markdown()).isEqualTo("slow answer");
        assertThat(h.used(alice)).isEqualTo(1);
        assertThat(h.server.requestCount()).isEqualTo(1);
        assertThat(data.sessionRows(otherSession)).isZero();
        assertThat(data.messageRows(otherSession)).isZero();
    }

    @Test
    void onceTheFirstRequestIsDoneTheStudentCanSendAgain() throws Exception {
        ChatFlowHarness h = start();
        h.server.enqueueDelayed(300, 200, GeminiResponses.onTopic("slow answer"));
        h.server.enqueue(200, GeminiResponses.onTopic("quick answer"));
        Future<ChatReply> first = pool.submit(() -> h.send(alice, session, "slow question"));
        h.awaitProviderRequests(1);
        result(first);

        assertThat(h.send(alice, session, "next question").markdown()).isEqualTo("quick answer");
    }

    @Test
    void withALimitOfOneSimultaneousRequestsOfTheSameStudentLetExactlyOneThrough() throws Exception {
        ChatFlowHarness h = start(p -> p.setDailyLimit(1));
        h.server.enqueueDelayed(400, 200, GeminiResponses.onTopic("the only answer"));
        int threads = 6;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ChatReply>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            UUID ownSession = UUID.randomUUID();
            futures.add(pool.submit((Callable<ChatReply>) () -> {
                ready.countDown();
                go.await();
                return h.send(alice, ownSession, "simultaneous question");
            }));
        }
        ready.await();
        go.countDown();

        int succeeded = 0;
        for (Future<ChatReply> future : futures) {
            try {
                result(future);
                succeeded++;
            } catch (ChatRequestInFlightException | ChatQuotaExceededException expected) {
                // the loser is told to wait (409) or that the day is used up (429)
            }
        }

        assertThat(succeeded).isEqualTo(1);
        assertThat(h.used(alice)).isEqualTo(1);
        assertThat(data.usageRows(alice, ChatFlowHarness.DAY)).isEqualTo(1);
        assertThat(h.server.requestCount()).isEqualTo(1);
    }

    // ---- the global cap -------------------------------------------------------------------------------------

    @Test
    void whenEveryProviderSlotIsBusyAnotherStudentGetsBusyAndKeepsTheirQuota() throws Exception {
        ChatFlowHarness h = start(p -> p.setMaxConcurrent(1));
        h.server.enqueueDelayed(1_200, 200, GeminiResponses.onTopic("alice answer"));
        Future<ChatReply> first = pool.submit(() -> h.send(alice, session, "alice question"));
        h.awaitProviderRequests(1);
        UUID bobsSession = UUID.randomUUID();

        assertThatThrownBy(() -> h.send(bob, bobsSession, "bob question")).isInstanceOf(ChatBusyException.class);

        assertThat(result(first).markdown()).isEqualTo("alice answer");
        assertThat(h.used(bob)).isZero();
        assertThat(data.usageRows(bob)).isZero();
        assertThat(data.sessionRows(bobsSession)).isZero();
        assertThat(data.messageRowsForStudent(bob)).isZero();
        assertThat(h.server.requestCount()).isEqualTo(1);
    }

    @Test
    void aFreedSlotIsUsableByTheNextStudent() throws Exception {
        ChatFlowHarness h = start(p -> p.setMaxConcurrent(1));
        h.server.enqueue(200, GeminiResponses.onTopic("alice answer"));
        h.server.enqueue(200, GeminiResponses.onTopic("bob answer"));
        h.send(alice, session, "alice question");

        assertThat(h.send(bob, UUID.randomUUID(), "bob question").markdown()).isEqualTo("bob answer");
    }

    @Test
    void manyStudentsAtOnceNeverExceedTheSlotsAndTheTurnedAwayOnesAreNotCharged() throws Exception {
        int slots = 3;
        int students = 10;
        ChatFlowHarness h = start(p -> p.setMaxConcurrent(slots));
        for (int i = 0; i < students; i++) {
            h.server.enqueueDelayed(700, 200, GeminiResponses.onTopic("answer " + i));
        }
        List<UUID> ids = new ArrayList<>();
        List<UUID> sessions = new ArrayList<>();
        CountDownLatch ready = new CountDownLatch(students);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<ChatReply>> futures = new ArrayList<>();
        for (int i = 0; i < students; i++) {
            UUID student = UUID.randomUUID();
            UUID ownSession = UUID.randomUUID();
            ids.add(student);
            sessions.add(ownSession);
            futures.add(pool.submit((Callable<ChatReply>) () -> {
                ready.countDown();
                go.await();
                return h.send(student, ownSession, "question of " + student);
            }));
        }
        ready.await();
        go.countDown();

        int succeeded = 0;
        for (int i = 0; i < students; i++) {
            try {
                result(futures.get(i));
                succeeded++;
                assertThat(h.used(ids.get(i))).isEqualTo(1);
            } catch (ChatBusyException busy) {
                assertThat(h.used(ids.get(i))).isZero();
                assertThat(data.usageRows(ids.get(i))).isZero();
                assertThat(data.sessionRows(sessions.get(i))).isZero();
            }
        }

        assertThat(succeeded).isGreaterThanOrEqualTo(slots);
        assertThat(succeeded).isLessThan(students);
        assertThat(h.server.requestCount()).isEqualTo(succeeded);
    }

    // ---- the day changes while the provider thinks -------------------------------------------------------------

    @Test
    void anAnswerArrivingAfterMidnightIsChargedAndReportedForTheDayItWasChargedOn() throws Exception {
        ChatFlowHarness h = start();
        h.clock.set(Instant.parse("2026-10-28T16:59:59Z"));
        h.server.enqueueDelayed(800, 200, GeminiResponses.onTopic("late answer"));
        Future<ChatReply> call = pool.submit(() -> h.send(alice, session, "question just before midnight"));
        h.awaitProviderRequests(1);
        h.clock.set(PAST_MIDNIGHT);

        ChatReply reply = result(call);

        assertThat(reply.quota().used()).isEqualTo(1);
        assertThat(reply.quota().remaining()).isEqualTo(19);
        assertThat(reply.quota().resetAt()).isEqualTo(DAY_RESET);
        assertThat(h.used(alice)).isEqualTo(1);
        assertThat(data.used(alice, NEXT_DAY)).isZero();
        assertThat(data.usageRows(alice, NEXT_DAY)).isZero();
        assertThat(h.service.quota(ChatFlowHarness.student(alice)).resetAt())
                .isEqualTo(Instant.parse("2026-10-29T17:00:00Z"));
        assertThat(h.service.quota(ChatFlowHarness.student(alice)).used()).isZero();
    }

    @Test
    void aRefusalAfterMidnightRefundsTheChargedDayAndLeavesTheNewDayAlone() throws Exception {
        ChatFlowHarness h = start();
        h.clock.set(Instant.parse("2026-10-28T16:59:59Z"));
        data.setUsed(alice, ChatFlowHarness.DAY, 4);
        h.server.enqueueDelayed(800, 200, GeminiResponses.offTopic());
        Future<ChatReply> call = pool.submit(() -> h.send(alice, session, "off topic just before midnight"));
        h.awaitProviderRequests(1);
        h.clock.set(PAST_MIDNIGHT);

        ChatReply reply = result(call);

        assertThat(reply.onTopic()).isFalse();
        assertThat(h.used(alice)).isEqualTo(4);
        assertThat(data.usageRows(alice, NEXT_DAY)).isZero();
        assertThat(reply.quota().used()).isEqualTo(4);
        assertThat(reply.quota().resetAt()).isEqualTo(DAY_RESET);
    }

    @Test
    void aProviderFailureAfterMidnightRefundsTheChargedDay() throws Exception {
        ChatFlowHarness h = start();
        h.clock.set(Instant.parse("2026-10-28T16:59:59Z"));
        data.setUsed(alice, ChatFlowHarness.DAY, 7);
        h.server.enqueueDelayed(800, 500, "");
        Future<ChatReply> call = pool.submit(() -> h.send(alice, session, "question just before midnight"));
        h.awaitProviderRequests(1);
        h.clock.set(PAST_MIDNIGHT);

        assertThatThrownBy(() -> result(call)).isInstanceOf(ChatBusyException.class);

        assertThat(h.used(alice)).isEqualTo(7);
        assertThat(data.usageRows(alice, NEXT_DAY)).isZero();
    }

    @Test
    void theNextDayStartsWithAFullQuota() throws Exception {
        ChatFlowHarness h = start(p -> p.setDailyLimit(1));
        h.server.enqueue(200, GeminiResponses.onTopic("day one"));
        h.server.enqueue(200, GeminiResponses.onTopic("day two"));
        h.send(alice, session, "first question");
        assertThatThrownBy(() -> h.send(alice, session, "second question"))
                .isInstanceOf(ChatQuotaExceededException.class);

        h.clock.set(PAST_MIDNIGHT);

        assertThat(h.send(alice, session, "second question").quota().remaining()).isZero();
        assertThat(data.used(alice, NEXT_DAY)).isEqualTo(1);
        assertThat(h.used(alice)).isEqualTo(1);
    }
}
