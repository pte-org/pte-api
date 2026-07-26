package com.pte.notification.domain;

import com.pte.common.messaging.AbstractProcessedEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** notification's idempotency ledger, shared across all 4 topics it consumes. */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent extends AbstractProcessedEvent {
}
