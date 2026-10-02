package com.pte.itembank.dto.response;

import com.pte.itembank.TaskAuthoringRequirements;
import com.pte.itembank.TaskRuntimeContractDescriptor;

import java.util.List;

public record TaskTypeCapabilityResponse(
        String screenKey,
        int contractVersion,
        String profileKey,
        String behaviorKey,
        String rendererKey,
        int answerSchemaVersion,
        String scoringProfileKey,
        int scoringProfileVersion,
        String scoringMode,
        List<String> requiredClientCapabilities,
        String minSupportedAppVersion,
        String authoringContractKey,
        int authoringContractVersion,
        TaskAuthoringRequirements authoringRequirements,
        String status) {

    public static TaskTypeCapabilityResponse from(TaskRuntimeContractDescriptor contract) {
        return new TaskTypeCapabilityResponse(contract.screenKey(), contract.contractVersion(),
                contract.profileKey(), contract.behaviorKey(), contract.rendererKey(),
                contract.answerSchemaVersion(), contract.scoringProfileKey(), contract.scoringProfileVersion(),
                contract.scoringMode(), contract.requiredClientCapabilities(), contract.minSupportedAppVersion(),
                contract.authoringContractKey(), contract.authoringContractVersion(),
                contract.authoringRequirements(), contract.status());
    }
}
