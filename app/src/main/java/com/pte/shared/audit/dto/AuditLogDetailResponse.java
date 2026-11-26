package com.pte.shared.audit.dto;

import tools.jackson.databind.JsonNode;

/**
 * One platform audit row with its before/after business snapshots as JSON objects (null when the command had no
 * snapshot, for example a creation has no {@code beforeState}).
 */
public record AuditLogDetailResponse(
        AuditLogResponse entry,
        JsonNode beforeState,
        JsonNode afterState) {
}
