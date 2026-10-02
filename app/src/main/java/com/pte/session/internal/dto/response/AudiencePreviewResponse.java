package com.pte.session.internal.dto.response;

import java.util.List;

public record AudiencePreviewResponse(
        int candidateCount,
        int duplicateCount,
        int eligibleCount,
        int excludedCount,
        int blockedCount,
        int capacity,
        boolean capacityReady,
        List<AudienceMemberResponse> members) {
}
