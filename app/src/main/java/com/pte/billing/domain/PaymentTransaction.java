package com.pte.billing.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Append-only receipt of a PayOS webhook attempt; only {@code processed} may change. */
@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(name = "idx_payment_transactions_order_code", columnList = "order_code"),
        @Index(name = "idx_payment_transactions_received_at", columnList = "received_at")
})
@Getter
@NoArgsConstructor
public class PaymentTransaction extends BaseEntity {

    @Column(name = "order_code", updatable = false)
    private Long orderCode;

    @Column(columnDefinition = "TEXT", nullable = false, updatable = false)
    private String rawPayload;

    @Column(nullable = false, updatable = false)
    private boolean signatureValid;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    private boolean processed;

    private PaymentTransaction(Long orderCode, String rawPayload, boolean signatureValid,
            Instant receivedAt) {
        this.orderCode = orderCode;
        this.rawPayload = rawPayload;
        this.signatureValid = signatureValid;
        this.receivedAt = receivedAt;
    }

    public static PaymentTransaction received(Long orderCode, String rawPayload,
            boolean signatureValid, Instant receivedAt) {
        return new PaymentTransaction(orderCode, rawPayload, signatureValid, receivedAt);
    }

    public void markProcessed() {
        processed = true;
    }
}
