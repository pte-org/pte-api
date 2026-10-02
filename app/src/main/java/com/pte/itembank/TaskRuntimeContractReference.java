package com.pte.itembank;

/** Minimal lookup key used by batch template/snapshot resolution. */
public record TaskRuntimeContractReference(String screenKey, int contractVersion) {
}
