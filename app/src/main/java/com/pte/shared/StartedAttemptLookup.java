package com.pte.shared;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Narrow read port for modules that need to know whether students already
 * started an attempt. The owning attempt module provides the implementation;
 * consumers depend on this shared contract instead of depending on attempt's
 * application service and creating a module cycle.
 */
public interface StartedAttemptLookup {

    Set<UUID> findStartedStudentPublicIds(UUID tenantId, List<UUID> sessionPublicIds,
            List<UUID> studentPublicIds);
}
