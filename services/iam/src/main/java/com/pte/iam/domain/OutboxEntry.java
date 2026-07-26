package com.pte.iam.domain;

import com.pte.common.messaging.AbstractOutboxEntry;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** iam's concrete outbox table (structure from {@link AbstractOutboxEntry}). */
@Entity
@Table(name = "outbox")
public class OutboxEntry extends AbstractOutboxEntry {
}
