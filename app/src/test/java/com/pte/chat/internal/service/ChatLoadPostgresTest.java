package com.pte.chat.internal.service;

import com.pte.chat.internal.exception.ChatBusyException;
import com.pte.chat.internal.exception.ChatQuotaExceededException;
import com.pte.chat.internal.exception.ChatRequestInFlightException;
import com.pte.chat.internal.prompt.ChatPrompts;
import com.pte.chat.internal.service.session.ChatConcurrencyGate.Lease;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scaled load model of the 70 requests/minute target (NFR-01) on a real Postgres, with only the provider faked.
 * Not a one-minute soak: time is compressed so it finishes in seconds while keeping the ratios that matter.
 *
 * <h3>Model</h3>
 * Real life: ~70 requests/minute, ~3 s provider latency, so ~3.5 calls in flight on average, against
 * {@code max-concurrent} 20. Here: 150 ms provider latency (20x faster), {@code max-concurrent} 4, daily limit 5,
 * 60 students, 300 sends. Students are a mix: 4 already at the limit (7 sends each, all must be refused with
 * 429), 6 heavy (12 sends each, they reach the limit), 50 normal (4 sends each). The fake provider fails
 * every 10th call with HTTP 500 and answers every 5th (offset 3) off-topic, so refunds are exercised.
 * <ul>
 * <li>Phase A, steady (150 sends): the test never lets more than {@code max-concurrent} sends overlap, so the
 *     global gate can never be full: the number of 503 from the gate must be exactly 0.</li>
 * <li>Phase B, bursts (150 sends in 10 bursts of 15 simultaneous sends, each burst drained before the next):
 *     15 senders against 4 slots. In one burst, at most {@code size - slots} sends can find every slot taken,
 *     because the gate refuses only while all slots are held and the first {@code slots} callers to reach it
 *     while it is empty always get one. So gate 503 over all bursts is at most the sum of
 *     {@code max(0, size - slots)} = 10 x 11 = 110 sends, which is 36.7 percent of the 300 sends and 73.3
 *     percent of the burst sends. That ratio follows from the configured sizes; it is not tuned to a run.</li>
 * </ul>
 * The observed counts are printed as one {@code CHAT-LOAD-RESULT} line and written to
 * {@code target/chat-load-test-result.txt}.
 */
@Testcontainers(disabledWithoutDocker = true)
class ChatLoadPostgresTest extends ChatFlowPostgresBase {

    private static final int SLOTS = 4;
    private static final int LIMIT = 5;
    private static final long LATENCY_MS = 150;
    private static final int STUDENTS = 60;
    private static final int AT_LIMIT_STUDENTS = 4;
    private static final int AT_LIMIT_SENDS = 7;
    private static final int HEAVY_STUDENTS = 6;
    private static final int HEAVY_SENDS = 12;
    private static final int NORMAL_SENDS = 4;
    private static final int PHASE_A_SENDS = 150;
    private static final int BURST_SIZE = 15;
    private static final int SCRIPT_LENGTH = 400;
    private static final long BURST_GAP_MS = 50;

    private enum Outcome { OK, REFUSED, IN_FLIGHT_409, BUSY_503, QUOTA_429 }

    private final Map<Integer, List<Outcome>> byStudent = new ConcurrentHashMap<>();
    private final List<Outcome> outcomes = new CopyOnWriteArrayList<>();
    private final List<String> unexpected = new CopyOnWriteArrayList<>();
    private final List<String> badReplies = new CopyOnWriteArrayList<>();
    private final List<UUID> ids = new ArrayList<>();

    @Test
    void sixtyStudentsThreeHundredSendsKeepQuotaExactAndTheGateLeakFree() throws Exception {
        try (ChatLoadStack stack = new ChatLoadStack(jdbc,
                p -> { p.setMaxConcurrent(SLOTS); p.setDailyLimit(LIMIT); }, LATENCY_MS, SCRIPT_LENGTH)) {
            for (int i = 0; i < STUDENTS; i++) {
                ids.add(UUID.randomUUID());
            }
            for (int i = 0; i < AT_LIMIT_STUDENTS; i++) {
                data.setUsed(ids.get(i), ChatFlowHarness.DAY, LIMIT);
            }
            List<Integer> atLimitSends = new ArrayList<>();
            List<Integer> rest = new ArrayList<>();
            for (int s = 0; s < STUDENTS; s++) {
                int sends = s < AT_LIMIT_STUDENTS ? AT_LIMIT_SENDS : s < AT_LIMIT_STUDENTS + HEAVY_STUDENTS
                        ? HEAVY_SENDS : NORMAL_SENDS;
                for (int n = 0; n < sends; n++) {
                    (s < AT_LIMIT_STUDENTS ? atLimitSends : rest).add(s);
                }
            }
            Collections.shuffle(rest, new Random(42));
            int total = atLimitSends.size() + rest.size();
            assertThat(total).as("scheduled sends").isEqualTo(300);

            List<Integer> phaseA = new ArrayList<>(atLimitSends);
            phaseA.addAll(rest.subList(0, PHASE_A_SENDS - atLimitSends.size()));
            Collections.shuffle(phaseA, new Random(7));
            List<Integer> phaseB = rest.subList(PHASE_A_SENDS - atLimitSends.size(), rest.size());
            assertThat(phaseA).hasSize(PHASE_A_SENDS);
            assertThat(phaseB.size() % BURST_SIZE).as("whole bursts").isZero();

            long started = System.nanoTime();
            runSteady(stack, phaseA);
            double gate503AfterSteady = stack.meter("chat.slot.rejected");
            int bursts = runBursts(stack, phaseB);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;

            verify(stack, total, bursts, gate503AfterSteady, elapsedMs);
        }
    }

    // ---- driving the load ----------------------------------------------------------------------------------

    /** At most SLOTS sends overlap, so the gate cannot be full whatever the timing. */
    private void runSteady(ChatLoadStack stack, List<Integer> sends) throws Exception {
        Semaphore overlap = new Semaphore(SLOTS);
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int seq = 0; seq < sends.size(); seq++) {
                int student = sends.get(seq);
                int n = seq;
                futures.add(pool.submit(() -> {
                    overlap.acquireUninterruptibly();
                    try {
                        attempt(stack, student, "steady-" + n);
                    } finally {
                        overlap.release();
                    }
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private int runBursts(ChatLoadStack stack, List<Integer> sends) throws Exception {
        int bursts = sends.size() / BURST_SIZE;
        ExecutorService pool = Executors.newFixedThreadPool(BURST_SIZE);
        try {
            for (int b = 0; b < bursts; b++) {
                CountDownLatch go = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < BURST_SIZE; i++) {
                    int student = sends.get(b * BURST_SIZE + i);
                    String tag = "burst-" + b + "-" + i;
                    futures.add(pool.submit(() -> {
                        go.await();
                        attempt(stack, student, tag);
                        return null;
                    }));
                }
                go.countDown();
                for (Future<?> future : futures) {
                    future.get();
                }
                Thread.sleep(BURST_GAP_MS);
            }
        } finally {
            pool.shutdownNow();
        }
        return bursts;
    }

    /** One send; its result is either a reply or one of the typed exceptions, anything else is recorded. */
    private void attempt(ChatLoadStack stack, int student, String tag) {
        Outcome outcome;
        try {
            ChatReply reply = stack.service.send(ChatFlowHarness.student(ids.get(student)),
                    new ChatSendCommand("load question " + tag + " of student " + student, null, "en"));
            outcome = reply.onTopic() ? Outcome.OK : Outcome.REFUSED;
            checkReply(reply, outcome);
        } catch (ChatRequestInFlightException ex) {
            outcome = Outcome.IN_FLIGHT_409;
        } catch (ChatBusyException ex) {
            outcome = Outcome.BUSY_503;
        } catch (ChatQuotaExceededException ex) {
            outcome = Outcome.QUOTA_429;
        } catch (Throwable ex) {
            unexpected.add(tag + ": " + ex);
            return;
        }
        outcomes.add(outcome);
        byStudent.computeIfAbsent(student, k -> new CopyOnWriteArrayList<>()).add(outcome);
    }

    private void checkReply(ChatReply reply, Outcome outcome) {
        if (reply.quota().used() > LIMIT || reply.quota().used() < 0 || reply.quota().remaining() < 0
                || reply.quota().limit() != LIMIT) {
            badReplies.add("quota out of range: " + reply.quota());
        }
        if (outcome == Outcome.REFUSED && !ChatPrompts.refusal("en").equals(reply.markdown())) {
            badReplies.add("refusal text is not the fixed one");
        }
        if (outcome == Outcome.OK && (reply.markdown() == null || reply.markdown().isBlank())) {
            badReplies.add("blank answer");
        }
    }

    // ---- checks ----------------------------------------------------------------------------------------------

    private long count(Outcome outcome) {
        return outcomes.stream().filter(o -> o == outcome).count();
    }

    private void verify(ChatLoadStack stack, int total, int bursts, double gate503AfterSteady, long elapsedMs)
            throws IOException {
        // Every send ended as a reply or a typed exception; nothing else escaped.
        assertThat(unexpected).as("exceptions of any other type").isEmpty();
        assertThat(badReplies).isEmpty();
        assertThat(outcomes).as("every send accounted for").hasSize(total);

        long ok = count(Outcome.OK);
        long refused = count(Outcome.REFUSED);
        long conflict = count(Outcome.IN_FLIGHT_409);
        long busy = count(Outcome.BUSY_503);
        long quota429 = count(Outcome.QUOTA_429);
        long gate503 = Math.round(stack.meter("chat.slot.rejected"));

        // Provider: one call per charged send that got past the gate, no retries; script never ran out.
        Map<String, Object> counted = stack.data.dailyTotals(ChatFlowHarness.DAY);
        long okRows = ((Number) counted.get("ok")).longValue();
        long refusedRows = ((Number) counted.get("refused")).longValue();
        long failedRows = ((Number) counted.get("failed")).longValue();
        assertThat(okRows).as("counted OK == successful replies").isEqualTo(ok);
        assertThat(refusedRows).as("counted REFUSED == off-topic replies").isEqualTo(refused);
        long gateRefusals = gate503;
        long providerFailures = busy - gateRefusals;
        assertThat(failedRows).as("counted FAILED == 503 that were not the gate").isEqualTo(providerFailures);
        assertThat(stack.providerCalls.get()).as("provider calls == OK + REFUSED + FAILED (no retries)")
                .isEqualTo((int) (ok + refused + failedRows));
        assertThat(stack.server.requestCount()).isEqualTo(stack.providerCalls.get());
        assertThat(stack.providerCalls.get()).as("script was not exhausted").isLessThan(SCRIPT_LENGTH);
        assertThat(stack.maxProviderCallsInFlight.get()).as("provider calls in flight never above the slots")
                .isBetween(1, SLOTS);

        // Per-student quota: never above the limit, equal to the seed plus the successful sends.
        for (int s = 0; s < STUDENTS; s++) {
            int seed = s < AT_LIMIT_STUDENTS ? LIMIT : 0;
            long studentOk = byStudent.getOrDefault(s, List.of()).stream().filter(o -> o == Outcome.OK).count();
            int used = stack.data.used(ids.get(s), ChatFlowHarness.DAY);
            assertThat(used).as("student %d used", s).isLessThanOrEqualTo(LIMIT)
                    .isEqualTo((int) (seed + studentOk));
            boolean reachedTheCharge = byStudent.getOrDefault(s, List.of()).stream().anyMatch(o ->
                    o == Outcome.OK || o == Outcome.REFUSED || o == Outcome.QUOTA_429) || seed > 0;
            int usageRows = stack.data.usageRows(ids.get(s));
            assertThat(usageRows).as("student %d chat_usage rows", s).isLessThanOrEqualTo(1);
            if (reachedTheCharge) {
                assertThat(usageRows).as("student %d chat_usage rows", s).isEqualTo(1);
            }
            assertThat(stack.data.usageRows(ids.get(s), ChatFlowHarness.DAY)).isEqualTo(usageRows);
        }
        Integer strayDays = jdbc.queryForObject("SELECT count(*) FROM chat_usage WHERE usage_date <> :d",
                new MapSqlParameterSource("d", ChatFlowHarness.DAY), Integer.class);
        assertThat(strayDays).as("usage rows on another day").isZero();
        long totalUsed = jdbc.queryForObject("SELECT coalesce(sum(used), 0) FROM chat_usage", new MapSqlParameterSource(),
                Long.class);
        assertThat(totalUsed).as("sum of counters == seeded + OK sends").isEqualTo((long) AT_LIMIT_STUDENTS * LIMIT + ok);

        // Refunds: every off-topic and failed call restored the counter, none of the refunds failed.
        assertThat(Math.round(stack.meter("chat.quota.refunded"))).as("refunds == REFUSED + FAILED")
                .isEqualTo(refused + failedRows);
        assertThat(stack.meter("chat.quota.refund.failed")).isZero();
        assertThat(Math.round(stack.meter("chat.inflight.rejected"))).as("409 meter").isEqualTo(conflict);
        assertThat(refused + failedRows).as("refunds were exercised").isPositive();
        assertThat(quota429).as("the quota (429) path was exercised").isPositive();
        assertThat(ok).as("some sends succeeded").isPositive();

        // The gate: none in steady state, bounded in bursts.
        long gateBound = (long) bursts * Math.max(0, BURST_SIZE - SLOTS);
        assertThat(gate503AfterSteady).as("gate 503 while at most SLOTS sends overlap").isZero();
        assertThat(gate503).as("gate 503 <= sum over bursts of (size - slots)").isLessThanOrEqualTo(gateBound);
        assertThat(gate503).as("the gate refused something under bursts (path exercised)").isPositive();

        // No leak: every slot and every student lock is free again.
        List<Lease> held = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            held.add(stack.gate.acquireSlot());
        }
        assertThatThrownBy(stack.gate::acquireSlot).isInstanceOf(ChatBusyException.class);
        held.forEach(Lease::close);
        for (UUID id : ids) {
            stack.gate.lockStudent(id).close();
        }

        report(total, ok, refused, conflict, busy, gate503, providerFailures, quota429, stack, gateBound, elapsedMs);
    }

    private void report(int total, long ok, long refused, long conflict, long busy, long gate503, long providerFailures,
                        long quota429, ChatLoadStack stack, long gateBound, long elapsedMs) throws IOException {
        String line = String.format(java.util.Locale.ROOT, "CHAT-LOAD-RESULT sends=%d ok=%d refused_offtopic=%d http409=%d http503=%d "
                        + "(gate=%d provider_failure=%d) http429=%d provider_calls=%d peak_in_flight=%d/%d "
                        + "gate503_bound=%d (%.1f%% of sends) gate503_observed=%.1f%% elapsed_ms=%d",
                total, ok, refused, conflict, busy, gate503, providerFailures, quota429, stack.providerCalls.get(),
                stack.maxProviderCallsInFlight.get(), SLOTS, gateBound, 100.0 * gateBound / total,
                100.0 * gate503 / total, elapsedMs);
        System.out.println(line);
        Path file = Path.of("target", "chat-load-test-result.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8);
        EnumMap<Outcome, Long> summary = new EnumMap<>(Outcome.class);
        for (Outcome outcome : Outcome.values()) {
            summary.put(outcome, count(outcome));
        }
        System.out.println("CHAT-LOAD-OUTCOMES " + summary);
    }
}
