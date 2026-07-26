package com.pte.authoring.service;

import com.pte.common.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Read/write scope rules for authoring content (ADR-001 visibility model):
 * SHARED is world-readable, platform-write-only; PRIVATE is readable/writable only
 * by its owning tenant (platform users may read across tenants for support).
 */
@Component
public class AuthoringAccessPolicy {

    public boolean canRead(UUID entityTenantId, boolean shared, CurrentUser caller) {
        if (shared) {
            return true;
        }
        if (caller.isPlatformUser()) {
            return true;
        }
        return entityTenantId != null && entityTenantId.equals(caller.tenantId());
    }

    public boolean canWriteShared(CurrentUser caller) {
        return caller.isPlatformUser();
    }
}
