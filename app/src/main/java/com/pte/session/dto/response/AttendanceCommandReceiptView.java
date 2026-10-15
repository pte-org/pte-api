package com.pte.session.dto.response;

import com.pte.session.domain.enums.AttendanceStatus;

import java.time.Instant;
import java.util.UUID;

public record AttendanceCommandReceiptView(
        UUID requestPublicId,
        UUID sessionPublicId,
        UUID studentPublicId,
        UUID enrollmentPublicId,
        UUID actorPublicId,
        String actorRole,
        Long appliedVersion,
        AttendanceStatus previousStatus,
        AttendanceStatus newStatus,
        String reasonOrNote,
        Instant serverActionAt,
        boolean changed,
        String committedResult,
        AttendanceRowView currentAttendance
) {
}
