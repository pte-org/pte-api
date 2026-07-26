package com.pte.reporting.domain;

import com.pte.common.messaging.AbstractProcessedEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** reporting's idempotency ledger, shared across all three topics it consumes. */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent extends AbstractProcessedEvent {
}
