package com.pte.itembank;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Profiles come from the code-owned registry: no stored value can introduce a renderer or scorer. */
class TaskRuntimeProfileServiceTest {

    private final TaskRuntimeProfileService service = new TaskRuntimeProfileService();

    @Test
    void batchResolution_returnsTheRegistryProfileOfEveryRequestedTaskType() {
        Map<String, TaskRuntimeProfileDescriptor> result =
                service.resolveActiveByTaskTypeCodes(List.of("READ_ALOUD", "WRITE_ESSAY"));

        assertThat(result).containsKeys("READ_ALOUD", "WRITE_ESSAY");
        assertThat(result.get("READ_ALOUD").rendererKey()).isEqualTo("READ_ALOUD_V1");
    }

    @Test
    void pinnedValidation_acceptsTheRegistryProfilesOfAWholeTemplate() {
        Set<TaskRuntimeProfileDescriptor> invalid = service.invalidPinnedProfiles(List.of(
                TaskRuntimeProfileRegistry.descriptorFor("READ_ALOUD"),
                TaskRuntimeProfileRegistry.descriptorFor("WRITE_ESSAY")));

        assertThat(invalid).isEmpty();
    }

    @Test
    void pinnedValidation_rejectsAProfileWhoseRendererIsNotAllowlisted() {
        TaskRuntimeProfileDescriptor registry = TaskRuntimeProfileRegistry.descriptorFor("READ_ALOUD");
        TaskRuntimeProfileDescriptor forged = new TaskRuntimeProfileDescriptor(registry.taskTypeCode(),
                registry.profileKey(), registry.profileVersion(), registry.behaviorKey(), "com.example.EvilRenderer",
                registry.answerSchemaVersion(), registry.scoringProfileKey(), registry.scoringProfileVersion(),
                registry.requiredClientCapabilities(), registry.status());

        assertThat(service.invalidPinnedProfiles(List.of(forged))).containsExactly(forged);
    }
}
