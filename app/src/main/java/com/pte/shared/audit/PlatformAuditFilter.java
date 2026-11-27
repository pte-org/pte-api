package com.pte.shared.audit;

import java.time.LocalDate;
import java.util.UUID;

/** Optional filters of the platform audit list; every null field is ignored and the rest combine with AND. */
public record PlatformAuditFilter(
        String aggregateType,
        String aggregateId,
        UUID actorId,
        String search,
        LocalDate createdFrom,
        LocalDate createdTo) {
}
