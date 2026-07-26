package com.pte.scoring.domain;

import com.pte.common.messaging.AbstractProcessedEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** scoring's idempotency ledger, shared across BOTH topics it consumes. */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent extends AbstractProcessedEvent {
}
