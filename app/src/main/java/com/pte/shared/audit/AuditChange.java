package com.pte.shared.audit;

import java.util.UUID;

/**
 * One audited state change. The JSON states are optional snapshots of the aggregate before and after the
 * command, so the audit trail can answer history and receipt lookups for the request.
 */
public record AuditChange(
        String aggregateType,
        String aggregateId,
        String action,
        String summary,
        UUID requestPublicId,
        String beforeStateJson,
        String afterStateJson) {
}
