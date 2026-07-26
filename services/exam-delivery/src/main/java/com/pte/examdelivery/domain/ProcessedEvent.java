package com.pte.examdelivery.domain;

import com.pte.common.messaging.AbstractProcessedEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** exam-delivery's idempotency ledger — first needed in Phase 10 (its first Kafka consumer). */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent extends AbstractProcessedEvent {
}
