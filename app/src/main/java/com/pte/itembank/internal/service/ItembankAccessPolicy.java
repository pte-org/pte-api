package com.pte.itembank.internal.service;

import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Read/write scope rules for itembank content (ADR-001 visibility model):
 * SHARED is world-readable, platform-write-only; PRIVATE is readable/writable only
 * by its owning tenant (platform users may read across tenants for support).
 */
@Component
public class ItembankAccessPolicy {

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
