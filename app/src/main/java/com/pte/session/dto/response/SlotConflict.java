package com.pte.session.dto.response;

import java.util.UUID;

/** Identifies the existing exam that prevents a student from joining a slot. */
public record SlotConflict(UUID studentPublicId, UUID conflictingSessionPublicId,
        String conflictingSessionName) {
}
