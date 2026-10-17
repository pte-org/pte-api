package com.pte.session.dto.event;

import com.pte.session.dto.response.SlotConflict;

import java.util.List;
import java.util.UUID;

/** Published after a bulk enrollment commits with students excluded by slot conflicts. */
public record StudentEnrollmentConflictEvent(UUID sessionPublicId, UUID tenantId, String sessionName,
        List<SlotConflict> conflicts) {
}
