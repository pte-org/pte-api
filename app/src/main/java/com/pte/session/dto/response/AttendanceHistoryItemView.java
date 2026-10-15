package com.pte.session.dto.response;

import com.pte.session.domain.enums.AttendanceStatus;

import java.time.Instant;
import java.util.UUID;

public record AttendanceHistoryItemView(
        UUID id,
        UUID requestPublicId,
        UUID studentPublicId,
        UUID enrollmentPublicId,
        UUID actorPublicId,
        String actorDisplayName,
        String actorRole,
        Long appliedVersion,
        AttendanceStatus previousStatus,
        AttendanceStatus newStatus,
        String reasonOrNote,
        Instant serverActionAt
) {
}
