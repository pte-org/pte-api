package com.pte.practice.internal.service;

import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.dto.response.QuestionTypeResponse;
import com.pte.itembank.dto.response.TaskTypeReadinessResponse;
import com.pte.practice.internal.dto.request.PracticePreflightRequest;
import com.pte.practice.internal.dto.response.PracticeCatalogAvailability;
import com.pte.practice.internal.dto.response.PracticeCatalogResponse;
import com.pte.practice.internal.dto.response.PracticeCatalogTaskResponse;
import com.pte.practice.internal.dto.response.PracticePreflightResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeCatalogServiceTest {

    @Mock
    private QuestionTypeService questionTypeService;

    private PracticeCatalogService service;

    @BeforeEach
    void setUp() {
        service = new PracticeCatalogService(questionTypeService);
    }

    @Test
    void catalogContainsSafeMetadataAndFallsBackToVisibleCanonicalTaskVocabulary() {
        when(questionTypeService.list(true)).thenReturn(List.of(active("READ_ALOUD", "SPEAKING")));

        PracticeCatalogResponse catalog = service.getCatalog();

        assertThat(catalog.productCode()).isEqualTo("PTE_CORE_PRACTICE");
        assertThat(catalog.sections()).hasSize(4);
        assertThat(catalog.sections().stream().flatMap(section -> section.taskTypes().stream()))
                .extracting(PracticeCatalogTaskResponse::code)
                .contains("READ_ALOUD", "WRITE_FROM_DICTATION", "MC_READING_SINGLE");
        assertThat(catalog.sections().stream().flatMap(section -> section.taskTypes().stream()))
                .allSatisfy(task -> assertThat(task.requiredClientCapabilities()).isNotNull());
    }

    @Test
    void tamperedRuntimeProfileIsVisibleButNotRunnable() {
        TaskRuntimeProfileDescriptor expected = TaskRuntimeProfileRegistry.descriptorFor("READ_ALOUD");
        TaskRuntimeProfileDescriptor tampered = new TaskRuntimeProfileDescriptor(
                expected.taskTypeCode(), expected.profileKey(), expected.profileVersion(), "UNTRUSTED",
                expected.rendererKey(), expected.answerSchemaVersion(), expected.scoringProfileKey(),
                expected.scoringProfileVersion(), expected.requiredClientCapabilities(), expected.status());
        when(questionTypeService.list(true)).thenReturn(List.of(withRuntime("READ_ALOUD", "SPEAKING", tampered)));

        PracticeCatalogTaskResponse task = find(service.getCatalog(), "READ_ALOUD");

        assertThat(task.availability()).isEqualTo(PracticeCatalogAvailability.UNAVAILABLE);
        assertThat(task.unavailableReason()).isEqualTo("PRACTICE_UNSUPPORTED_RUNTIME");
        assertThat(task).extracting(PracticeCatalogTaskResponse::profileKey)
                .isEqualTo(expected.profileKey());
    }

    @Test
    void taskWithoutRuntimeProfileIsReturnedAsUnavailableInsteadOfFailingCatalogLoad() {
        when(questionTypeService.list(true)).thenReturn(List.of(
                withRuntime("UNKNOWN_RUNTIME_TASK", "READING", null)));

        PracticeCatalogTaskResponse task = find(service.getCatalog(), "UNKNOWN_RUNTIME_TASK");

        assertThat(task.availability()).isEqualTo(PracticeCatalogAvailability.UNAVAILABLE);
        assertThat(task.unavailableReason()).isEqualTo("PRACTICE_UNSUPPORTED_RUNTIME");
        assertThat(task.rendererKey()).isNull();
    }

    @Test
    void preflightReportsMissingDeviceCapabilitiesBeforeStart() {
        when(questionTypeService.list(true)).thenReturn(List.of(active("MC_READING_SINGLE", "READING")));

        PracticePreflightResponse response = service.preflight(new PracticePreflightRequest(
                "PTE_CORE_PRACTICE", Set.of("MC_READING_SINGLE"), null));

        assertThat(response.ready()).isFalse();
        assertThat(response.blockedTaskTypes()).isEmpty();
        assertThat(response.missingCapabilities()).contains("OPTION_SELECTION");
    }

    @Test
    void serverNotReadyContentRemainsVisibleAndBlocksPreflight() {
        QuestionTypeResponse response = withRuntime("READ_ALOUD", "SPEAKING",
                TaskRuntimeProfileRegistry.descriptorFor("READ_ALOUD"),
                new TaskTypeReadinessResponse(false, "UNSUPPORTED", List.of()));
        when(questionTypeService.list(true)).thenReturn(List.of(response));

        PracticePreflightResponse result = service.preflight(new PracticePreflightRequest(
                "PTE_CORE_PRACTICE", Set.of("READ_ALOUD"),
                new com.pte.practice.internal.dto.request.PracticeCapabilityManifest(
                        Set.of("AUDIO_RECORDING"), "1.0.0")));

        assertThat(result.ready()).isFalse();
        assertThat(result.blockedTaskTypes()).containsExactly("READ_ALOUD");
    }

    private QuestionTypeResponse active(String code, String section) {
        return withRuntime(code, section, TaskRuntimeProfileRegistry.descriptorFor(code));
    }

    private QuestionTypeResponse withRuntime(String code, String section,
            TaskRuntimeProfileDescriptor runtime) {
        return withRuntime(code, section, runtime, null);
    }

    private QuestionTypeResponse withRuntime(String code, String section,
            TaskRuntimeProfileDescriptor runtime, TaskTypeReadinessResponse readiness) {
        return new QuestionTypeResponse(UUID.randomUUID(), code, code, code, section, true, true, 1,
                false, false, true, false, true, false, false, false, runtime, code, code,
                runtime == null ? null : runtime.screenKey(), runtime == null ? null : runtime.contractVersion(),
                readiness, null);
    }

    private PracticeCatalogTaskResponse find(PracticeCatalogResponse catalog, String code) {
        return catalog.sections().stream().flatMap(section -> section.taskTypes().stream())
                .filter(task -> task.code().equals(code)).findFirst().orElseThrow();
    }
}
