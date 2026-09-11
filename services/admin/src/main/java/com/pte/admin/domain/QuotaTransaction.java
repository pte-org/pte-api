package com.pte.admin.domain;

import com.pte.admin.domain.enums.QuotaActionType;
import com.pte.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * An immutable ledger row for a Tenant's package/quota allocation — never
 * updated after creation, only appended to. {@code amount} is a signed
 * delta (positive for {@code GRANTED}), never a running total; the current
 * total is the sum of all rows for a tenant. This is what makes it an audit
 * trail rather than a renamed mutable counter. No back-reference collection
 * on {@link Tenant} on purpose — nothing needs to navigate tenant→
 * transactions in memory, only tenant→[persisted rows via repository], so
 * there's no cascaded-collection footgun to worry about (see Organization's
 * Phase 4 merge/persist lesson).
 */
@Entity
@Table(name = "quota_transactions", indexes = {@Index(name = "idx_quota_transactions_tenant", columnList = "tenant_id")})
@Getter
@Setter
@NoArgsConstructor
public class QuotaTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String packageName;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private QuotaActionType actionType;

    @Column(nullable = false)
    private UUID actorUserId;

    private String note;
}
