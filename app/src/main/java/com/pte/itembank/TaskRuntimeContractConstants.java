package com.pte.itembank;

/** Stable mapping labels shared by snapshot publication and attempt pinning. */
public final class TaskRuntimeContractConstants {

    public static final String MAPPING_VERSION_CANONICAL = "CANONICAL_V1";
    public static final String MAPPING_VERSION_LEGACY = "LEGACY_PTE_V1";
    public static final String MAPPING_STATUS_RESOLVED_CANONICAL = "RESOLVED_CANONICAL";
    public static final String MAPPING_STATUS_RESOLVED_LEGACY = "RESOLVED_LEGACY";
    public static final String MAPPING_STATUS_INCOMPATIBLE = "INCOMPATIBLE";

    private TaskRuntimeContractConstants() {
    }
}
