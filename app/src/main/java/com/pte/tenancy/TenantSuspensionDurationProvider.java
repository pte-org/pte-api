package com.pte.tenancy;

/** Supplies the platform default used when an administrator omits a duration. */
@FunctionalInterface
public interface TenantSuspensionDurationProvider {

    int defaultSuspensionDays();
}
