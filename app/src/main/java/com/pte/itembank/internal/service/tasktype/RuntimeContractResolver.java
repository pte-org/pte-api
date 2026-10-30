package com.pte.itembank.internal.service.tasktype;

import com.pte.itembank.internal.exception.tasktype.TaskTypeCapabilityNotFoundException;
import com.pte.itembank.domain.QuestionType;
import com.pte.itembank.TaskRuntimeContractService;
import com.pte.itembank.TaskRuntimeContractDescriptor;
import com.pte.itembank.TaskTypeCodeCompatibility;
import org.springframework.stereotype.Component;

/**
 * Resolves runtime contract metadata for task types from the contract service.
 */
@Component
public class RuntimeContractResolver {

    private final TaskRuntimeContractService contractService;

    public RuntimeContractResolver(TaskRuntimeContractService contractService) {
        this.contractService = contractService;
    }

    /**
     * Resolve the dynamic runtime contract for a definition.
     * Returns null if the contract is not available or not yet configured.
     */
    public TaskRuntimeContractDescriptor resolveDynamic(QuestionType definition) {
        if (definition.getScreenKey() != null && definition.getRuntimeProfileVersion() != null
                && contractService != null) {
            try {
                return contractService.resolveReadable(definition.getScreenKey(),
                        definition.getRuntimeProfileVersion());
            } catch (RuntimeException ignored) {
                // A retired contract remains readable through the stored binding;
                // the canonical response marks it unavailable for new authoring.
            }
        }
        if (contractService != null && definition.getTaskTypeKey() != null
                && TaskTypeCodeCompatibility.isStandard(definition.getTaskTypeKey())) {
            return contractService.fromLegacyProfile(definition.getTaskTypeKey());
        }
        return null;
    }

    /** Resolves an active contract by screen key and version; any lookup failure means the capability is unknown. */
    public TaskRuntimeContractDescriptor resolveActive(String screenKey, int contractVersion) {
        try {
            return contractService.resolveActive(screenKey, contractVersion);
        } catch (RuntimeException ex) {
            throw new TaskTypeCapabilityNotFoundException();
        }
    }
}
