package com.pte.session.internal.dto.response;

import com.pte.session.domain.enums.AudienceDecisionReason;
import com.pte.session.domain.enums.AudienceMemberStatus;

import java.util.UUID;

public record AudienceMemberResponse(
        UUID studentPublicId,
        AudienceMemberStatus status,
        AudienceDecisionReason reason,
        String sourceSummary,
        UUID priorSessionPublicId,
        String priorSessionName,
        String priorStatus) {
}
