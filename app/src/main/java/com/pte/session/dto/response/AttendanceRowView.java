package com.pte.session.dto.response;

import com.pte.session.domain.enums.AttendanceStatus;

import java.time.Instant;
import java.util.UUID;

public record AttendanceRowView(
        UUID studentPublicId,
        UUID enrollmentPublicId,
        String displayName,
        String email,
        AttendanceStatus status,
        Long version,
        UUID lastActorPublicId,
        String lastActorRole,
        Instant lastActionAt,
        String lastReasonOrNote,
        boolean canEdit,
        String blockingReason,
        String latestAttemptActivityStatus,
        Instant latestAttemptActivityAt,
        String identityStatus,
        String identityNote
) {
}
