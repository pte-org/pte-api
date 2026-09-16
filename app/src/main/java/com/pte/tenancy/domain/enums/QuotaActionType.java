package com.pte.tenancy.domain.enums;

/**
 * {@code DEDUCTED}/{@code REVOKED} are declared now so a future
 * deduct-on-exam-start flow reuses this ledger instead of needing a second
 * migration â€” this phase only ever writes {@code GRANTED} rows.
 */
public enum QuotaActionType {
    GRANTED,
    DEDUCTED,
    REVOKED
}
