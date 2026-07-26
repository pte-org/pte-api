package com.pte.reporting.domain;

import com.pte.common.messaging.AbstractOutboxEntry;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** reporting's concrete outbox table — emits {@code AttemptPublished} only. */
@Entity
@Table(name = "outbox")
public class OutboxEntry extends AbstractOutboxEntry {
}
