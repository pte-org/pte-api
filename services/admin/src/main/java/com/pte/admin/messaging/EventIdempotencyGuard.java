package com.pte.admin.messaging;

import com.pte.admin.domain.ProcessedEvent;
import com.pte.admin.repository.ProcessedEventRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Coordinates the at-least-once event ledger with an inbound projection.
 * Call both methods from the consumer's single transaction: check before the
 * business write and mark only after that write succeeds.
 */
@Component
public class EventIdempotencyGuard {

    private final ProcessedEventRepository repository;

    public EventIdempotencyGuard(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    public boolean alreadyProcessed(UUID eventId) {
        return repository.existsById(eventId);
    }

    public void markProcessed(UUID eventId) {
        ProcessedEvent processedEvent = new ProcessedEvent();
        processedEvent.setEventId(eventId);
        repository.save(processedEvent);
    }
}
