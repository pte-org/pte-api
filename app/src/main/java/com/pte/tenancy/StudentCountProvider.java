package com.pte.tenancy;

import java.util.UUID;

/** Public port used by tenancy to read the identity module's student count. */
@FunctionalInterface
public interface StudentCountProvider {

    long countStudents(UUID tenantId);
}
