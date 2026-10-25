package com.pte.session.internal.service;

import com.pte.session.domain.enums.AttendanceStatus;

import java.util.UUID;

/** Before/after snapshot written to the audit trail for one attendance change. */
record AttendanceChangeState(
        UUID studentPublicId,
        UUID registrationPublicId,
        AttendanceStatus status,
        long version,
        String reasonOrNote,
        String actorRole) {
}
