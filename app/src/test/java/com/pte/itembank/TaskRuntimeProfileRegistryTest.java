package com.pte.itembank;

import com.pte.itembank.domain.enums.PteTaskType;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TaskRuntimeProfileRegistryTest {

    @Test
    void registry_hasExactlyOneAllowlistedProfilePerStandardTaskType() {
        assertThat(TaskRuntimeProfileRegistry.all()).hasSize(PteTaskType.values().length).doesNotHaveDuplicates();
        assertThat(TaskRuntimeProfileRegistry.all().stream().map(TaskRuntimeProfileDescriptor::taskTypeCode)
                .collect(Collectors.toSet())).hasSize(23);
    }

    @Test
    void personalIntroduction_isExplicitlyUnscoredAndEveryScoredTypeHasScoringKey() {
        TaskRuntimeProfileDescriptor personalIntroduction =
                TaskRuntimeProfileRegistry.descriptorFor("PERSONAL_INTRODUCTION");

        assertThat(personalIntroduction.scoringProfileKey()).isEqualTo("UNSCORED");
        assertThat(TaskRuntimeProfileRegistry.all().stream()
                .filter(profile -> !profile.taskTypeCode().equals("PERSONAL_INTRODUCTION"))
                .map(TaskRuntimeProfileDescriptor::scoringProfileKey)
                .collect(Collectors.toSet())).contains("AI_SPEECH", "AI_TEXT", "OBJECTIVE");
    }

    @Test
    void profileKeysAndRendererKeys_areStableSemanticIdentifiers() {
        Set<String> rendererKeys = TaskRuntimeProfileRegistry.all().stream()
                .map(TaskRuntimeProfileDescriptor::rendererKey)
                .collect(Collectors.toSet());

        assertThat(rendererKeys).allMatch(key -> key.endsWith("_V1"));
        assertThat(rendererKeys).noneMatch(key -> key.contains(".") || key.contains("/") || key.contains("Class"));
    }
}
