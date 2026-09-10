package com.pte.admin.dto.response;

import java.util.UUID;

/**
 * Shape doubles as both a single membership's response (assign/unassign/transfer)
 * and one row of the tenant-wide {@code GET /class-memberships} roster — same
 * fields serve both, so Phase 7/10/13 can reuse this DTO as-is.
 */
public record ClassMembershipResponse(
        UUID publicId,
        UUID classPublicId,
        String className,
        UUID programPublicId,
        String programName,
        UUID studentPublicId) {
}
