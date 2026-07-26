package com.pte.authoring.domain.enums;

/**
 * Question ownership scope. SHARED = platform-owned bank ({@code tenant_id} null),
 * readable by every host, writable only by PLATFORM_AUTHOR. PRIVATE = owned by one
 * host's tenant, invisible to others (ADR-001 visibility model).
 */
public enum Visibility {
    SHARED,
    PRIVATE
}
