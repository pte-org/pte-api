package com.pte.scoring.internal.rubric.service;

import com.pte.assessment.ExamDefinitionService;
import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.dto.response.RubricParametersView;
import com.pte.scoring.dto.response.TaskTypeParametersView;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.rubric.RubricTraits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.CATALOG;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.CODEC;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.child;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.defaultsJson;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.editTask;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Hosts and Examiners see the parameters of an ACTIVE or RETIRED pinned version only — never a draft. */
class PinnedRubricParametersServiceTest {

    private static final List<String> ATTEMPT_TASK_TYPES = List.of("DESCRIBE_IMAGE", "WRITE_ESSAY",
            "MC_READING_SINGLE");

    private final ScoringRubricVersionRepository versions = mock(ScoringRubricVersionRepository.class);
    private final PinnedRubricParametersService service = new PinnedRubricParametersService(versions,
            new RubricResolver(versions, CATALOG, CODEC, mock(ExamDefinitionService.class)));

    @ParameterizedTest
    @EnumSource(value = RubricVersionStatus.class, names = {"ACTIVE", "RETIRED"})
    void activeAndRetiredVersionsShowTheirStoredParametersForTheAttemptsTaskTypes(RubricVersionStatus status) {
        ScoringRubricVersion pinned = stored(status, editTask("DESCRIBE_IMAGE",
                task -> child(task, RubricConstants.PARAM_TRAIT_WEIGHTS).put(RubricTraits.CONTENT, 3)));

        RubricParametersView view = service.findReadable(pinned.getPublicId(), ATTEMPT_TASK_TYPES).orElseThrow();

        assertThat(view.versionPublicId()).isEqualTo(pinned.getPublicId());
        assertThat(view.status()).isEqualTo(status.name());
        assertThat(view.taskTypes()).extracting(TaskTypeParametersView::taskType)
                .containsExactlyInAnyOrder("DESCRIBE_IMAGE", "WRITE_ESSAY");
        TaskTypeParametersView describeImage = task(view, "DESCRIBE_IMAGE");
        assertThat(describeImage.traitWeights()).containsEntry(RubricTraits.CONTENT, 3)
                .containsEntry(RubricTraits.PRONUNCIATION, 5);
        assertThat(describeImage.gateEnabled()).containsEntry(RubricTraits.CONTENT, true);
        assertThat(describeImage.formBands()).isNull();
        assertThat(describeImage.azureMappingVersion()).isEqualTo("1");
        assertThat(describeImage.azureMappingCalibrated()).isFalse();
        assertThat(describeImage.azureMappingLabel()).isEqualTo(RubricConstants.AZURE_MAPPING_UNCALIBRATED_LABEL);
        TaskTypeParametersView essay = task(view, "WRITE_ESSAY");
        assertThat(essay.formBands()).hasSize(3);
        assertThat(essay.azureMappingVersion()).isNull();
        assertThat(essay.azureMappingLabel()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = RubricVersionStatus.class, names = {"DRAFT", "PENDING", "REJECTED"})
    void aDraftPendingOrRejectedVersionIsNeverShown(RubricVersionStatus status) {
        ScoringRubricVersion draft = stored(status, defaultsJson());

        assertThat(service.findReadable(draft.getPublicId(), ATTEMPT_TASK_TYPES)).isEmpty();
    }

    @Test
    void aMissingOrUnknownVersionShowsNothing() {
        UUID unknown = UUID.randomUUID();
        when(versions.findByPublicId(unknown)).thenReturn(Optional.empty());

        assertThat(service.findReadable(null, ATTEMPT_TASK_TYPES)).isEmpty();
        assertThat(service.findReadable(unknown, ATTEMPT_TASK_TYPES)).isEmpty();
    }

    private ScoringRubricVersion stored(RubricVersionStatus status, String json) {
        ScoringRubricVersion version = version(status, json);
        when(versions.findByPublicId(version.getPublicId())).thenReturn(Optional.of(version));
        return version;
    }

    private static TaskTypeParametersView task(RubricParametersView view, String taskType) {
        return view.taskTypes().stream().filter(task -> task.taskType().equals(taskType)).findFirst().orElseThrow();
    }
}
