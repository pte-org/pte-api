package com.pte.shared.audit;

/**
 * One audited command on a shared catalogue resource together with business snapshots of the aggregate before
 * and after it. The states are plain records built by the owning module (never entities); {@code before} is null
 * for a creation and {@code after} is null for a deletion.
 */
public record AuditStateChange(
        String aggregateType,
        String aggregateId,
        String resourceName,
        String resourceCode,
        String action,
        String summary,
        Object before,
        Object after) {
}
