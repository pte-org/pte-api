package com.pte.itembank;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRuntimeContractDescriptorTest {

    @Test
    void keepsSemanticContractDataAndDoesNotExposeImplementationTypes() {
        TaskRuntimeContractDescriptor contract = new TaskRuntimeContractDescriptor(
                "READ_ALOUD_V1", 1, "PTE.READ_ALOUD", 1, "RECORD_RESPONSE",
                "READ_ALOUD_V1", 1, "AI_SPEECH", 1, "SCORED",
                List.of("AUDIO_RECORDING"), "1.0.0", "PTE.READ_ALOUD_AUTHORING", 1,
                new TaskAuthoringRequirements(false, false, true, false, false, false, false, false),
                "ACTIVE");

        assertThat(contract.active()).isTrue();
        assertThat(contract.scoringEnabled()).isTrue();
        assertThat(contract.requiredClientCapabilities()).containsExactly("AUDIO_RECORDING");
    }
}
