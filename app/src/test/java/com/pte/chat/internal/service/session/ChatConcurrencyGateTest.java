package com.pte.chat.internal.service.session;

import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.exception.ChatBusyException;
import com.pte.chat.internal.exception.ChatRequestInFlightException;
import com.pte.chat.internal.service.ChatObservability;
import com.pte.chat.internal.service.session.ChatConcurrencyGate.Lease;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** One request at a time per student and a global cap on provider calls; neither ever waits. */
class ChatConcurrencyGateTest {

    private final ChatObservability observability = mock(ChatObservability.class);
    private final ChatAiProperties properties = new ChatAiProperties();
    private ChatConcurrencyGate gate;

    @BeforeEach
    void setUp() {
        properties.setMaxConcurrent(2);
        gate = new ChatConcurrencyGate(properties, observability);
    }

    // ---- per-student lock --------------------------------------------------------------------------------

    @Test
    void aSecondRequestOfTheSameStudentIsRejectedWhileTheFirstIsRunning() {
        UUID student = UUID.randomUUID();

        try (Lease lease = gate.lockStudent(student)) {
            assertThat(lease).isNotNull();
            assertThatThrownBy(() -> gate.lockStudent(student)).isInstanceOf(ChatRequestInFlightException.class);
        }
    }

    @Test
    void anotherStudentIsNotBlocked() {
        try (Lease first = gate.lockStudent(UUID.randomUUID());
             Lease second = gate.lockStudent(UUID.randomUUID())) {
            assertThat(first).isNotNull();
            assertThat(second).isNotNull();
        }
    }

    @Test
    void closingTheLeaseFreesTheStudent() {
        UUID student = UUID.randomUUID();
        gate.lockStudent(student).close();

        assertThatCode(() -> gate.lockStudent(student).close()).doesNotThrowAnyException();
    }

    @Test
    void closingALeaseTwiceIsHarmlessAndDoesNotFreeSomeoneElsesLock() {
        UUID student = UUID.randomUUID();
        Lease first = gate.lockStudent(student);
        first.close();
        Lease second = gate.lockStudent(student);

        first.close();

        assertThatThrownBy(() -> gate.lockStudent(student)).isInstanceOf(ChatRequestInFlightException.class);
        second.close();
        assertThatCode(() -> gate.lockStudent(student).close()).doesNotThrowAnyException();
    }

    @Test
    void aRejectedStudentLockIsCounted() {
        UUID student = UUID.randomUUID();
        Lease lease = gate.lockStudent(student);

        assertThatThrownBy(() -> gate.lockStudent(student)).isInstanceOf(ChatRequestInFlightException.class);
        assertThatThrownBy(() -> gate.lockStudent(student)).isInstanceOf(ChatRequestInFlightException.class);

        verify(observability, times(2)).inFlightRejected();
        verify(observability, never()).slotRejected();
        lease.close();
    }

    @Test
    void aGrantedStudentLockIsNotCountedAsRejected() {
        gate.lockStudent(UUID.randomUUID()).close();

        verifyNoInteractions(observability);
    }

    @Test
    void ofManySimultaneousRequestsOfOneStudentExactlyOneGetsTheLock() throws Exception {
        UUID student = UUID.randomUUID();
        int threads = 40;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger granted = new AtomicInteger();
            AtomicInteger rejected = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try (Lease ignored = gate.lockStudent(student)) {
                        granted.incrementAndGet();
                        release.await();
                    } catch (ChatRequestInFlightException ex) {
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            ready.await();
            go.countDown();
            while (granted.get() + rejected.get() < threads) {
                if (granted.get() == 1 && rejected.get() == threads - 1) {
                    break;
                }
                Thread.sleep(5);
            }
            release.countDown();
            for (Future<?> future : futures) {
                future.get();
            }

            assertThat(granted.get()).isEqualTo(1);
            assertThat(rejected.get()).isEqualTo(threads - 1);
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- global slots ------------------------------------------------------------------------------------

    @Test
    void slotsRunOutAtMaxConcurrentAndTheNextRequestIsTurnedAwayAtOnce() {
        Lease first = gate.acquireSlot();
        Lease second = gate.acquireSlot();

        assertThatThrownBy(() -> gate.acquireSlot()).isInstanceOf(ChatBusyException.class);

        first.close();
        second.close();
    }

    @Test
    void closingASlotMakesItAvailableAgain() {
        Lease first = gate.acquireSlot();
        Lease second = gate.acquireSlot();
        first.close();

        Lease third = gate.acquireSlot();

        assertThatThrownBy(() -> gate.acquireSlot()).isInstanceOf(ChatBusyException.class);
        second.close();
        third.close();
    }

    @Test
    void closingASlotTwiceReleasesItOnlyOnce() {
        Lease first = gate.acquireSlot();
        Lease second = gate.acquireSlot();

        first.close();
        first.close();

        Lease third = gate.acquireSlot();
        assertThatThrownBy(() -> gate.acquireSlot()).isInstanceOf(ChatBusyException.class);
        second.close();
        third.close();
    }

    @Test
    void theSlotCountFollowsTheConfiguration() {
        properties.setMaxConcurrent(1);
        gate = new ChatConcurrencyGate(properties, observability);
        Lease only = gate.acquireSlot();

        assertThatThrownBy(() -> gate.acquireSlot()).isInstanceOf(ChatBusyException.class);

        only.close();
        assertThatCode(() -> gate.acquireSlot().close()).doesNotThrowAnyException();
    }

    @Test
    void aRejectedSlotIsCounted() {
        Lease first = gate.acquireSlot();
        Lease second = gate.acquireSlot();

        assertThatThrownBy(() -> gate.acquireSlot()).isInstanceOf(ChatBusyException.class);

        verify(observability).slotRejected();
        verify(observability, never()).inFlightRejected();
        first.close();
        second.close();
    }

    @Test
    void theStudentLockAndTheSlotsAreIndependent() {
        UUID student = UUID.randomUUID();
        Lease lock = gate.lockStudent(student);
        Lease first = gate.acquireSlot();
        Lease second = gate.acquireSlot();

        assertThatThrownBy(() -> gate.acquireSlot()).isInstanceOf(ChatBusyException.class);
        assertThatThrownBy(() -> gate.lockStudent(student)).isInstanceOf(ChatRequestInFlightException.class);

        lock.close();
        first.close();
        second.close();
    }

    @Test
    void underHeavyContentionNeverMoreThanMaxConcurrentSlotsAreHeld() throws Exception {
        int maxConcurrent = 5;
        properties.setMaxConcurrent(maxConcurrent);
        gate = new ChatConcurrencyGate(properties, observability);
        int threads = 64;
        int roundsPerThread = 40;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger holding = new AtomicInteger();
            AtomicInteger peak = new AtomicInteger();
            AtomicInteger acquired = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    for (int round = 0; round < roundsPerThread; round++) {
                        try (Lease ignored = gate.acquireSlot()) {
                            int now = holding.incrementAndGet();
                            peak.accumulateAndGet(now, Math::max);
                            acquired.incrementAndGet();
                            Thread.yield();
                            holding.decrementAndGet();
                        } catch (ChatBusyException busy) {
                            Thread.yield();
                        }
                    }
                    return null;
                }));
            }
            ready.await();
            go.countDown();
            for (Future<?> future : futures) {
                future.get();
            }

            assertThat(peak.get()).isLessThanOrEqualTo(maxConcurrent);
            assertThat(acquired.get()).isPositive();
            // everything was released: all slots can be taken again
            List<Lease> all = new ArrayList<>();
            for (int i = 0; i < maxConcurrent; i++) {
                all.add(gate.acquireSlot());
            }
            assertThatThrownBy(() -> gate.acquireSlot()).isInstanceOf(ChatBusyException.class);
            all.forEach(Lease::close);
        } finally {
            pool.shutdownNow();
        }
    }
}
