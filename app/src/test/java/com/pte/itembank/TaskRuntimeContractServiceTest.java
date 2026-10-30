package com.pte.itembank;

import com.pte.itembank.internal.exception.tasktype.TaskRuntimeProfileException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Contracts come from the code-owned registry, so nothing outside the release allowlist can be resolved. */
class TaskRuntimeContractServiceTest {

    private final TaskRuntimeContractService service = new TaskRuntimeContractService();

    @Test
    void activeResolution_returnsTheContractOfAnAllowlistedScreen() {
        TaskRuntimeContractDescriptor resolved = service.resolveActive("READ_ALOUD_V1", 1);

        assertThat(resolved.screenKey()).isEqualTo("READ_ALOUD_V1");
        assertThat(resolved.contractVersion()).isEqualTo(1);
        assertThat(resolved.active()).isTrue();
    }

    @Test
    void readableResolution_matchesActiveResolutionForAnActiveContract() {
        assertThat(service.resolveReadable("READ_ALOUD_V1", 1).status())
                .isEqualTo(service.resolveActive("READ_ALOUD_V1", 1).status());
    }

    @Test
    void resolution_rejectsAScreenOutsideTheReleaseAllowlist() {
        assertThatThrownBy(() -> service.resolveActive("UNKNOWN_SCREEN_V1", 1))
                .isInstanceOf(TaskRuntimeProfileException.class)
                .hasMessage("TASK_RUNTIME_PROFILE_NOT_FOUND");
    }

    @Test
    void resolution_rejectsAnUnknownContractVersion() {
        assertThatThrownBy(() -> service.resolveReadable("READ_ALOUD_V1", 99))
                .isInstanceOf(TaskRuntimeProfileException.class);
    }

    @Test
    void listSelectable_offersOneContractPerStandardTaskType() {
        assertThat(service.listSelectable()).hasSameSizeAs(TaskRuntimeProfileRegistry.all());
    }
}
