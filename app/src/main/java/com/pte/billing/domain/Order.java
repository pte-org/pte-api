package com.pte.billing.domain;

import com.pte.billing.domain.enums.OrderStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A tenant's immutable price snapshot and payment lifecycle for one purchase. */
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_tenant_created", columnList = "tenant_id,created_at"),
        @Index(name = "idx_orders_status_created", columnList = "status,created_at")
})
@Getter
@NoArgsConstructor
public class Order extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID planId;

    @Column(nullable = false, unique = true)
    private Long orderCode;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(length = 512)
    private String paymentLinkUrl;

    @Column
    private Instant paidAt;

    private Order(UUID tenantId, UUID planId, Long orderCode, BigDecimal amount, String currency) {
        this.tenantId = tenantId;
        this.planId = planId;
        this.orderCode = orderCode;
        this.amount = amount;
        this.currency = currency;
    }

    public static Order pending(UUID tenantId, UUID planId, Long orderCode,
            BigDecimal amount, String currency) {
        return new Order(tenantId, planId, orderCode, amount, currency);
    }

    public void attachPaymentLink(String paymentLinkUrl) {
        this.paymentLinkUrl = paymentLinkUrl;
    }

    public void markPaid(Instant paidAt) {
        if (status == OrderStatus.PENDING) {
            status = OrderStatus.PAID;
            this.paidAt = paidAt;
        }
    }

    public void expire() {
        if (status == OrderStatus.PENDING) {
            status = OrderStatus.EXPIRED;
        }
    }

    public void cancel() {
        if (status == OrderStatus.PENDING) {
            status = OrderStatus.CANCELLED;
        }
    }
}
