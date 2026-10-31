package com.pte.chat.internal.service.session;

import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.exception.ChatBusyException;
import com.pte.chat.internal.exception.ChatRequestInFlightException;
import com.pte.chat.internal.service.ChatObservability;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Two non-blocking limits: one request in flight per student (a second one is rejected at once, which also
 * stops double clicks) and a global number of provider calls. A lease frees its slot exactly once, so closing
 * it twice cannot release a newer owner's lock. In-memory, so it is correct for a single backend instance.
 */
@Component
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatConcurrencyGate {

    public interface Lease extends AutoCloseable {
        @Override
        void close();
    }

    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final Semaphore slots;
    private final ChatObservability observability;

    public ChatConcurrencyGate(ChatAiProperties properties, ChatObservability observability) {
        this.slots = new Semaphore(properties.getMaxConcurrent());
        this.observability = observability;
    }

    public Lease lockStudent(UUID studentId) {
        if (!inFlight.add(studentId)) {
            observability.inFlightRejected();
            throw new ChatRequestInFlightException();
        }
        return once(() -> inFlight.remove(studentId));
    }

    public Lease acquireSlot() {
        if (!slots.tryAcquire()) {
            observability.slotRejected();
            throw new ChatBusyException();
        }
        return once(slots::release);
    }

    private static Lease once(Runnable release) {
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (closed.compareAndSet(false, true)) {
                release.run();
            }
        };
    }
}
