package com.pte.billing.domain.enums;

/** Lifecycle states for one individually issued activation code. */
public enum LicenseCodeStatus {
    ISSUED,
    REDEEMED,
    REVOKED,
    EXPIRED
}
