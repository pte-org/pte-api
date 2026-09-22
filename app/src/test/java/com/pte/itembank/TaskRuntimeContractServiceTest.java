package com.pte.itembank;

import com.pte.itembank.domain.TaskRuntimeContract;
import com.pte.itembank.internal.repository.TaskRuntimeContractRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskRuntimeContractServiceTest {

    @Mock
    private TaskRuntimeContractRepository repository;

    @Test
    void readableResolution_keepsRetiredContractVisibleForCatalogHistory() {
        TaskRuntimeContract contract = contract("RETIRED");
        when(repository.findByScreenKeyAndContractVersionAndDeletedFalse("READ_ALOUD_V1", 1))
                .thenReturn(Optional.of(contract));

        TaskRuntimeContractDescriptor resolved = new TaskRuntimeContractService(repository)
                .resolveReadable("READ_ALOUD_V1", 1);

        assertThat(resolved.status()).isEqualTo("RETIRED");
        assertThat(resolved.screenKey()).isEqualTo("READ_ALOUD_V1");
        assertThat(resolved.contractVersion()).isEqualTo(1);
    }

    @Test
    void activeResolution_rejectsRetiredContractForNewAuthoring() {
        when(repository.findByScreenKeyAndContractVersionAndDeletedFalse("READ_ALOUD_V1", 1))
                .thenReturn(Optional.of(contract("RETIRED")));

        assertThatThrownBy(() -> new TaskRuntimeContractService(repository)
                .resolveActive("READ_ALOUD_V1", 1))
                .hasMessage("TASK_RUNTIME_PROFILE_NOT_ACTIVE");
    }

    private TaskRuntimeContract contract(String status) {
        TaskRuntimeProfileDescriptor profile = TaskRuntimeProfileRegistry.descriptorFor("READ_ALOUD");
        TaskRuntimeContract contract = new TaskRuntimeContract();
        contract.setScreenKey(profile.rendererKey());
        contract.setContractVersion(profile.profileVersion());
        contract.setProfileKey(profile.profileKey());
        contract.setProfileVersion(profile.profileVersion());
        contract.setBehaviorKey(profile.behaviorKey());
        contract.setRendererKey(profile.rendererKey());
        contract.setAnswerSchemaVersion(profile.answerSchemaVersion());
        contract.setScoringProfileKey(profile.scoringProfileKey());
        contract.setScoringProfileVersion(profile.scoringProfileVersion());
        contract.setScoringMode("SCORED");
        contract.setRequiredClientCapabilities(profile.requiredClientCapabilities().toArray(String[]::new));
        contract.setMinSupportedAppVersion("1.0.0");
        contract.setAuthoringContractKey("PTE.READ_ALOUD_AUTHORING");
        contract.setAuthoringContractVersion(1);
        contract.setStatus(status);
        return contract;
    }
}
