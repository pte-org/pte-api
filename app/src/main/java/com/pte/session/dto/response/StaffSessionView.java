package com.pte.session.dto.response;

import java.time.Instant;
import java.util.UUID;

public record StaffSessionView(UUID sessionPublicId, String name, String sessionCode, String sessionStatus,
        Instant opensAt, Instant closesAt) {
}
