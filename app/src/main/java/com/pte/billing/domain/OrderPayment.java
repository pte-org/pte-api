package com.pte.billing.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Append-only receipt of a verified PayOS notification for one order; only {@code processed} may change. Forged
 * notifications, and signed ones for an unknown order or with a mismatching amount, are logged and rejected, never
 * stored, so the table only grows with real orders.
 */
@Entity
@Table(name = "order_payments", indexes = {
        @Index(name = "idx_order_payments_order", columnList = "order_id"),
        @Index(name = "idx_order_payments_received_at", columnList = "received_at")
})
@Getter
@NoArgsConstructor
public class OrderPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(columnDefinition = "TEXT", nullable = false, updatable = false)
    private String rawPayload;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    private boolean processed;

    OrderPayment(Order order, String rawPayload, Instant receivedAt) {
        this.order = order;
        this.rawPayload = rawPayload;
        this.receivedAt = receivedAt;
    }

    public void markProcessed() {
        processed = true;
    }
}
