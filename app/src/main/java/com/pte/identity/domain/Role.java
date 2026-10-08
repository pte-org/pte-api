package com.pte.identity.domain;

/**
 * Platform-wide role taxonomy. Platform roles have no tenant; the rest are
 * scoped to the user's tenant. Emitted into the JWT {@code roles} claim.
 */
public enum Role {
    PLATFORM_ADMIN,
    PLATFORM_MANAGER,
    ACADEMIC_MANAGER,
    ACADEMIC_STAFF,
    /** @deprecated use ACADEMIC_STAFF; retained during the role migration window. */
    @Deprecated
    PLATFORM_AUTHOR,
    HOST_ADMIN,
    PROCTOR,
    EXAMINER,
    STUDENT
}
