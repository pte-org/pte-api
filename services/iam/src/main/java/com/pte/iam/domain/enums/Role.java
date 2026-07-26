package com.pte.iam.domain.enums;

/**
 * Platform-wide role taxonomy (ADR-004). Platform roles have no tenant; the rest
 * are scoped to the user's tenant. Emitted into the JWT {@code roles} claim.
 */
public enum Role {
    PLATFORM_ADMIN,
    PLATFORM_AUTHOR,
    HOST_ADMIN,
    HOST_AUTHOR,
    PROCTOR,
    STUDENT
}
