package com.pte.attempt.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Explicit compatibility switch for snapshots published before runtime provenance existed. */
@ConfigurationProperties(prefix = "attempt.capability")
public class CapabilityProperties {

    /** Only legacy snapshots with no runtime profile may use the grandfathering path. */
    private boolean allowLegacyMissingManifest = true;

    public boolean isAllowLegacyMissingManifest() {
        return allowLegacyMissingManifest;
    }

    public void setAllowLegacyMissingManifest(boolean allowLegacyMissingManifest) {
        this.allowLegacyMissingManifest = allowLegacyMissingManifest;
    }
}
