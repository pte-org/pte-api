package com.pte.scheduling.domain;

import com.pte.common.messaging.AbstractProcessedEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** scheduling's idempotency ledger (structure from {@link AbstractProcessedEvent}). */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent extends AbstractProcessedEvent {
}
