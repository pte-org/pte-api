package com.pte.practice;

import com.pte.shared.security.CurrentUser;

import java.util.UUID;

/**
 * Public seam other modules use to ask whether a student may use Practice. It exists so {@code chat}
 * never reaches into {@code practice.internal}; the server-side policy stays in one place.
 */
public interface PracticeEntitlementGate {

    /** Throws when the caller is not an unlocked student; fail-closed while strict entitlement is off. */
    void assertUnlocked(CurrentUser caller, UUID requestedOrganizationId);
}
