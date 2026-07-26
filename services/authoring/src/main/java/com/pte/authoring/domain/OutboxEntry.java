package com.pte.authoring.domain;

import com.pte.common.messaging.AbstractOutboxEntry;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** authoring's concrete outbox table (structure from {@link AbstractOutboxEntry}). */
@Entity
@Table(name = "outbox")
public class OutboxEntry extends AbstractOutboxEntry {
}
