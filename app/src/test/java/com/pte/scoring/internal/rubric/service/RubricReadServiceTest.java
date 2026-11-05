package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.rubric.dto.FormBandResponse;
import com.pte.scoring.internal.rubric.dto.RubricVersionResponse;
import com.pte.scoring.internal.rubric.dto.AzureBandThresholdResponse;
import com.pte.scoring.internal.rubric.dto.AzureMappingResponse;
import com.pte.scoring.internal.rubric.dto.TaskTypeRubricResponse;
import com.pte.scoring.internal.rubric.dto.TraitDetailsResponse;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.rubric.RubricTraits;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.CATALOG;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.CODEC;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.assertRubricVersionError;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.child;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.editTask;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.seedJson;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** What the vendor rubric page reads: catalog structure and descriptions plus the version row's numbers. */
class RubricReadServiceTest {

    private final ScoringRubricVersionRepository versions = mock(ScoringRubricVersionRepository.class);
    private final RubricReadService service = new RubricReadService(versions, CATALOG,
            new RubricResolver(versions, CATALOG, CODEC,
                org.mockito.Mockito.mock(com.pte.assessment.ExamDefinitionService.class)));

    @Test
    void listsAll22TaskTypesOfTheActiveVersionWithItsNumbers() {
        ScoringRubricVersion active = active(seedJson());

        List<TaskTypeRubricResponse> rubrics = service.listTaskTypes(null);

        assertThat(rubrics).hasSize(22);
        assertThat(rubrics).allSatisfy(rubric -> {
            assertThat(rubric.versionPublicId()).isEqualTo(active.getPublicId());
            assertThat(rubric.catalogVersion()).isEqualTo(RubricConstants.CATALOG_VERSION);
        });
    }

    @Test
    void aTraitScoredSpeakingTaskShowsTraitsLevelsGateAndTheUncalibratedAzureLabel() {
        active(seedJson());

        TaskTypeRubricResponse describeImage = byType(service.listTaskTypes(null)).get("DESCRIBE_IMAGE");

        TraitDetailsResponse content = describeImage.traits().get(0);
        assertThat(content.key()).isEqualTo(RubricTraits.CONTENT);
        assertThat(content.maxScore()).isEqualTo(6);
        assertThat(content.weight()).isEqualTo(6);
        assertThat(content.levels()).hasSize(7).allSatisfy(level -> assertThat(level.text()).isNotBlank());
        assertThat(content.pearsonPages()).isNotBlank();
        assertThat(describeImage.gates()).singleElement()
                .satisfies(gate -> assertThat(gate.enabled()).isTrue());
        assertThat(describeImage.formBands()).isNull();
        assertThat(describeImage.objectiveRule()).isNull();
        assertThat(describeImage.azureMapping().calibrated()).isFalse();
        assertThat(describeImage.azureMapping().version()).isEqualTo("1");
        assertThat(describeImage.azureMapping().label()).isEqualTo(RubricConstants.AZURE_MAPPING_UNCALIBRATED_LABEL);
    }

    /** Phase 5: the uncalibrated table itself (thresholds and input rules) is part of the read API. */
    @Test
    void theAzureMappingShowsItsThresholdsAndInputRulesForEverySpeakingTask() {
        active(seedJson());
        Map<String, TaskTypeRubricResponse> all = byType(service.listTaskTypes(null));

        for (String speaking : List.of("READ_ALOUD", "REPEAT_SENTENCE", "DESCRIBE_IMAGE", "RE_TELL_LECTURE",
                "RESPOND_TO_A_SITUATION", "SUMMARIZE_GROUP_DISCUSSION")) {
            AzureMappingResponse mapping = all.get(speaking).azureMapping();
            assertThat(mapping.calibrated()).as(speaking).isFalse();
            assertThat(mapping.label()).isEqualTo(RubricConstants.AZURE_MAPPING_UNCALIBRATED_LABEL);
            assertThat(mapping.pronunciationInput()).isEqualTo(RubricConstants.AZURE_PRONUNCIATION_INPUT_VI);
            assertThat(mapping.oralFluencyInput()).isEqualTo(RubricConstants.AZURE_ORAL_FLUENCY_INPUT_VI);
            assertThat(mapping.pronunciationBands()).extracting(AzureBandThresholdResponse::band)
                    .containsExactly(1, 2, 3, 4, 5);
            assertThat(mapping.oralFluencyBands()).extracting(AzureBandThresholdResponse::minAzureScore)
                    .containsExactly(30.0, 45.0, 60.0, 75.0, 90.0);
        }
        // Answer Short Question has no Azure-scored trait (Vocabulary is matched in code).
        assertThat(all.get("ANSWER_SHORT_QUESTION").azureMapping()).isNull();
    }

    @Test
    void readAloudContentIsPerWordAndEssayShowsItsFormBands() {
        active(seedJson());
        Map<String, TaskTypeRubricResponse> all = byType(service.listTaskTypes(null));

        TraitDetailsResponse readAloudContent = all.get("READ_ALOUD").traits().get(0);
        assertThat(readAloudContent.perWord()).isTrue();
        assertThat(readAloudContent.maxScore()).isNull();
        assertThat(all.get("WRITE_ESSAY").formBands()).contains(new FormBandResponse(200, 300, 2));
        assertThat(all.get("WRITE_ESSAY").azureMapping()).isNull();
    }

    @Test
    void anObjectiveTaskShowsItsRuleAndNoTraits() {
        active(seedJson());

        TaskTypeRubricResponse single = service.getTaskType("MC_READING_SINGLE", null);

        assertThat(single.traits()).isEmpty();
        assertThat(single.objectiveRule().code()).isEqualTo(RubricConstants.OBJECTIVE_CORRECT_INCORRECT);
        assertThat(single.azureMapping()).isNull();
    }

    @Test
    void weightsShownAreTheStoredOnesNotTheCodeDefaults() {
        active(editTask("DESCRIBE_IMAGE",
                task -> child(task, RubricConstants.PARAM_TRAIT_WEIGHTS).put(RubricTraits.CONTENT, 2)));

        TaskTypeRubricResponse describeImage = service.getTaskType("DESCRIBE_IMAGE", null);

        assertThat(describeImage.traits().get(0).weight()).isEqualTo(2);
    }

    @Test
    void aRequestedVersionIsUsedInsteadOfTheActiveOne() {
        ScoringRubricVersion retired = version(RubricVersionStatus.RETIRED, seedJson());
        when(versions.findByPublicId(retired.getPublicId())).thenReturn(Optional.of(retired));

        assertThat(service.getTaskType("WRITE_ESSAY", retired.getPublicId()).versionPublicId())
                .isEqualTo(retired.getPublicId());
    }

    @Test
    void anUnknownTaskTypeIs404BeforeAnyVersionIsRead() {
        assertRubricVersionError(() -> service.getTaskType("NOT_A_TASK", null), HttpStatus.NOT_FOUND,
                RubricConstants.RUBRIC_TASK_TYPE_UNKNOWN);
        verifyNoInteractions(versions);
    }

    @Test
    void anUnknownVersionOrNoActiveVersionIs404() {
        UUID unknown = UUID.randomUUID();
        when(versions.findByPublicId(unknown)).thenReturn(Optional.empty());
        when(versions.findByStatus(RubricVersionStatus.ACTIVE)).thenReturn(Optional.empty());

        assertRubricVersionError(() -> service.listTaskTypes(unknown), HttpStatus.NOT_FOUND,
                RubricConstants.RUBRIC_VERSION_NOT_FOUND);
        assertRubricVersionError(() -> service.listTaskTypes(null), HttpStatus.NOT_FOUND,
                RubricConstants.RUBRIC_VERSION_NO_ACTIVE);
    }

    @Test
    void versionsAreListedNewestFirstWithTheirMetadata() {
        ScoringRubricVersion active = version(RubricVersionStatus.ACTIVE, seedJson());
        ScoringRubricVersion draft = version(RubricVersionStatus.DRAFT, seedJson());
        when(versions.findAllByOrderByVersionNoDesc()).thenReturn(List.of(draft, active));
        when(versions.findByPublicId(active.getPublicId())).thenReturn(Optional.of(active));

        List<RubricVersionResponse> listed = service.listVersions();
        RubricVersionResponse single = service.getVersion(active.getPublicId());

        assertThat(listed).extracting(RubricVersionResponse::status).containsExactly("DRAFT", "ACTIVE");
        assertThat(single.publicId()).isEqualTo(active.getPublicId());
        assertThat(single.authorUserPublicId()).isEqualTo(active.getAuthorUserPublicId());
    }

    private ScoringRubricVersion active(String json) {
        ScoringRubricVersion active = version(RubricVersionStatus.ACTIVE, json);
        when(versions.findByStatus(RubricVersionStatus.ACTIVE)).thenReturn(Optional.of(active));
        return active;
    }

    private static Map<String, TaskTypeRubricResponse> byType(List<TaskTypeRubricResponse> rubrics) {
        return rubrics.stream().collect(Collectors.toMap(TaskTypeRubricResponse::taskType, Function.identity()));
    }
}
