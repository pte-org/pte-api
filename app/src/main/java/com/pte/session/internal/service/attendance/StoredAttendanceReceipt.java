package com.pte.session.internal.service.attendance;

import com.pte.session.domain.enums.AttendanceStatus;

import java.time.Instant;
import java.util.UUID;

/** What the idempotency store keeps of an attendance command so a retry can replay its receipt. */
record StoredAttendanceReceipt(
        UUID requestPublicId,
        UUID sessionPublicId,
        UUID studentPublicId,
        UUID registrationPublicId,
        UUID actorPublicId,
        String actorRole,
        long appliedVersion,
        AttendanceStatus previousStatus,
        AttendanceStatus newStatus,
        String reasonOrNote,
        Instant serverActionAt,
        boolean changed,
        String committedResult) {
}
