package com.pte.itembank;

import com.pte.itembank.domain.TaskRuntimeProfile;
import com.pte.itembank.domain.enums.TaskRuntimeProfileStatus;
import com.pte.itembank.internal.repository.TaskRuntimeProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskRuntimeProfileServiceTest {

    @Mock
    private TaskRuntimeProfileRepository repository;

    @Test
    void batchResolution_usesOneActiveProfileQueryForTheWholeTemplate() {
        TaskRuntimeProfile readAloud = profile("READ_ALOUD");
        TaskRuntimeProfile writeEssay = profile("WRITE_ESSAY");
        when(repository.findAllByTaskTypeCodeInAndStatusAndDeletedFalse(any(),
                eq(TaskRuntimeProfileStatus.ACTIVE))).thenReturn(List.of(readAloud, writeEssay));

        Map<String, TaskRuntimeProfileDescriptor> result = new TaskRuntimeProfileService(repository)
                .resolveActiveByTaskTypeCodes(List.of("READ_ALOUD", "WRITE_ESSAY"));

        assertThat(result).containsKeys("READ_ALOUD", "WRITE_ESSAY");
        assertThat(result.get("READ_ALOUD").rendererKey()).isEqualTo("READ_ALOUD_V1");
        verify(repository, times(1)).findAllByTaskTypeCodeInAndStatusAndDeletedFalse(any(),
                eq(TaskRuntimeProfileStatus.ACTIVE));
    }

    @Test
    void activeResolution_rejectsAProfileWhoseRendererIsNotAllowlisted() {
        TaskRuntimeProfile invalid = profile("READ_ALOUD");
        invalid.setRendererKey("com.example.EvilRenderer");
        when(repository.findByTaskTypeCodeAndStatusAndDeletedFalse("READ_ALOUD", TaskRuntimeProfileStatus.ACTIVE))
                .thenReturn(Optional.of(invalid));

        assertThatThrownBy(() -> new TaskRuntimeProfileService(repository).resolveActive("READ_ALOUD"))
                .hasMessage("TASK_RUNTIME_PROFILE_NOT_ALLOWED");
    }

    @Test
    void pinnedValidation_usesOneQueryForAllTemplateProfiles() {
        TaskRuntimeProfile readAloud = profile("READ_ALOUD");
        TaskRuntimeProfile writeEssay = profile("WRITE_ESSAY");
        TaskRuntimeProfileDescriptor readDescriptor = TaskRuntimeProfileRegistry.descriptorFor("READ_ALOUD");
        TaskRuntimeProfileDescriptor writeDescriptor = TaskRuntimeProfileRegistry.descriptorFor("WRITE_ESSAY");
        when(repository.findAllByTaskTypeCodeInAndProfileVersionInAndDeletedFalse(any(), any()))
                .thenReturn(List.of(readAloud, writeEssay));

        Set<TaskRuntimeProfileDescriptor> invalid = new TaskRuntimeProfileService(repository)
                .invalidPinnedProfiles(List.of(readDescriptor, writeDescriptor));

        assertThat(invalid).isEmpty();
        verify(repository, times(1)).findAllByTaskTypeCodeInAndProfileVersionInAndDeletedFalse(any(), any());
    }

    private TaskRuntimeProfile profile(String taskTypeCode) {
        TaskRuntimeProfileDescriptor descriptor = TaskRuntimeProfileRegistry.descriptorFor(taskTypeCode);
        TaskRuntimeProfile profile = new TaskRuntimeProfile();
        profile.setTaskTypeCode(descriptor.taskTypeCode());
        profile.setProfileKey(descriptor.profileKey());
        profile.setProfileVersion(descriptor.profileVersion());
        profile.setBehaviorKey(descriptor.behaviorKey());
        profile.setRendererKey(descriptor.rendererKey());
        profile.setAnswerSchemaVersion(descriptor.answerSchemaVersion());
        profile.setScoringProfileKey(descriptor.scoringProfileKey());
        profile.setScoringProfileVersion(descriptor.scoringProfileVersion());
        profile.setRequiredClientCapabilities(String.join(",", descriptor.requiredClientCapabilities()));
        profile.setStatus(TaskRuntimeProfileStatus.ACTIVE);
        return profile;
    }
}
