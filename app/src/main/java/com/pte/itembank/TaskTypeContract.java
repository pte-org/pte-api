package com.pte.itembank;

import com.pte.itembank.domain.enums.PteSection;

/** Immutable logical task-type contract exposed by the itembank facade. */
public record TaskTypeContract(
        String taskTypeKey,
        String displayName,
        String normalizedDisplayName,
        String shortName,
        PteSection section,
        boolean active,
        boolean retired,
        TaskRuntimeContractDescriptor runtime) {
}
