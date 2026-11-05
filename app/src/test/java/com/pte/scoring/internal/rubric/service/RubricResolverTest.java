package com.pte.scoring.internal.rubric.service;

import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.FormBand;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.rubric.RubricParameters;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitDefinition;
import com.pte.scoring.rubric.TraitScale;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.node.ArrayNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.CODEC;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.assertRubricVersionError;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.child;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.defaults;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.defaultsJson;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.editTask;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The effective rubric is built only from the stored version's parameters; the catalog adds structure only. */
class RubricResolverTest {

    private static final String DI = "DESCRIBE_IMAGE";
    private static final String WE = "WRITE_ESSAY";

    private final ScoringRubricVersionRepository versions = mock(ScoringRubricVersionRepository.class);
    private final RubricCatalog catalog = spy(new RubricCatalogImpl());
    private final ExamDefinitionService examDefinitions = mock(ExamDefinitionService.class);
    private final RubricResolver resolver = new RubricResolver(versions, catalog, CODEC, examDefinitions);

    @Test
    void weightsGatesAndBandsComeFromTheStoredRowNotFromCodeDefaults() {
        String edited = editTask(DI, task -> {
            child(task, RubricConstants.PARAM_TRAIT_WEIGHTS).put(RubricTraits.CONTENT, 3);
            child(task, RubricConstants.PARAM_GATE_ENABLED).put(RubricTraits.CONTENT, false);
        });
        ScoringRubricVersion stored = stored(RubricVersionStatus.ACTIVE, edited);

        EffectiveRubric rubric = resolver.resolve(stored.getPublicId(), DI);

        assertThat(rubric.weightOf(RubricTraits.CONTENT)).isEqualTo(3);
        assertThat(rubric.isGateEnabled(RubricTraits.CONTENT)).isFalse();
        assertThat(rubric.parameters().azureMappingVersion()).isEqualTo("1");
        verify(catalog, never()).defaultParameters(anyString());
    }

    @Test
    void formBandsComeFromTheStoredRow() {
        String edited = editTask(WE, task -> {
            ArrayNode bands = task.putArray(RubricConstants.PARAM_FORM_BANDS);
            bands.addObject().put(RubricConstants.PARAM_BAND_MIN_WORDS, 150)
                    .put(RubricConstants.PARAM_BAND_MAX_WORDS, 350).put(RubricConstants.PARAM_BAND_SCORE, 2);
        });
        ScoringRubricVersion stored = stored(RubricVersionStatus.RETIRED, edited);

        EffectiveRubric rubric = resolver.resolve(stored.getPublicId(), WE);

        assertThat(rubric.effectiveFormRule().bands()).containsExactly(new FormBand(150, 350, 2));
        verify(catalog, never()).defaultParameters(anyString());
    }

    @Test
    void resolveActiveUsesTheActiveVersion() {
        ScoringRubricVersion active = version(RubricVersionStatus.ACTIVE,
                editTask(DI, task -> child(task, RubricConstants.PARAM_TRAIT_WEIGHTS).put(RubricTraits.CONTENT, 2)));
        when(versions.findByStatus(RubricVersionStatus.ACTIVE)).thenReturn(Optional.of(active));

        assertThat(resolver.resolveActiveVersion(DI).rubric().weightOf(RubricTraits.CONTENT)).isEqualTo(2);
    }

    @Test
    void noActiveVersionIsNotFound() {
        when(versions.findByStatus(RubricVersionStatus.ACTIVE)).thenReturn(Optional.empty());

        assertRubricVersionError(() -> resolver.resolveActiveVersion(DI), HttpStatus.NOT_FOUND,
                RubricConstants.RUBRIC_VERSION_NO_ACTIVE);
    }

    @Test
    void anUnknownVersionOrTaskTypeIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(versions.findByPublicId(unknown)).thenReturn(Optional.empty());
        ScoringRubricVersion stored = stored(RubricVersionStatus.ACTIVE, defaultsJson());

        assertRubricVersionError(() -> resolver.resolve(unknown, DI), HttpStatus.NOT_FOUND,
                RubricConstants.RUBRIC_VERSION_NOT_FOUND);
        assertRubricVersionError(() -> resolver.resolve(stored.getPublicId(), "PERSONAL_INTRODUCTION"),
                HttpStatus.NOT_FOUND, RubricConstants.RUBRIC_TASK_TYPE_UNKNOWN);
    }

    @Test
    void aStoredRowMissingACatalogTaskTypeFailsInsteadOfFallingBackToDefaults() {
        StoredRubricParameters defaults = defaults(new RubricCatalogImpl());
        Map<String, RubricParameters> tasks = new LinkedHashMap<>(defaults.taskTypes());
        Map<String, Map<String, TraitScale>> scales = new LinkedHashMap<>(defaults.traitScales());
        tasks.remove(DI);
        scales.remove(DI);
        String withoutDescribeImage = CODEC.encode(new StoredRubricParameters(1, tasks, scales));
        ScoringRubricVersion stored = stored(RubricVersionStatus.ACTIVE, withoutDescribeImage);

        assertRubricVersionError(() -> resolver.resolve(stored.getPublicId(), DI), HttpStatus.INTERNAL_SERVER_ERROR,
                RubricConstants.RUBRIC_VERSION_TASK_TYPE_MISSING);
    }

    @Test
    void effectiveRubricsCoverEveryCatalogTaskTypeInCatalogOrder() {
        Map<String, EffectiveRubric> all = resolver.effectiveRubrics(version(RubricVersionStatus.ACTIVE,
                defaultsJson()));

        List<String> catalogOrder = new RubricCatalogImpl().all().stream().map(TaskRubric::taskType).toList();
        assertThat(all.keySet()).containsExactlyElementsOf(catalogOrder).hasSize(22);
        verify(catalog, never()).defaultParameters(anyString());
    }

    @Test
    void aStoredScaleThatDiffersFromTheCatalogFailsClosed() {
        String drifted = editTask(DI, task -> child(task, RubricConstants.PARAM_TRAIT_SCALES)
                .put(RubricTraits.CONTENT, 5));
        ScoringRubricVersion stored = stored(RubricVersionStatus.ACTIVE, drifted);

        assertRubricVersionError(() -> resolver.resolve(stored.getPublicId(), DI), HttpStatus.INTERNAL_SERVER_ERROR,
                RubricConstants.RUBRIC_VERSION_SCALE_DRIFT);
    }

    @Test
    void aPerWordScaleStoredForAFixedTraitIsDrift() {
        String drifted = editTask(DI, task -> child(task, RubricConstants.PARAM_TRAIT_SCALES)
                .put(RubricTraits.CONTENT, RubricConstants.PARAM_SCALE_PER_WORD));
        ScoringRubricVersion stored = stored(RubricVersionStatus.ACTIVE, drifted);

        assertRubricVersionError(() -> resolver.resolve(stored.getPublicId(), DI), HttpStatus.INTERNAL_SERVER_ERROR,
                RubricConstants.RUBRIC_VERSION_SCALE_DRIFT);
    }

    @Test
    void aCatalogWhoseTraitMaximumChangedLaterCannotSilentlyRescaleAnOldVersion() {
        TaskRubric real = new RubricCatalogImpl().find(DI).orElseThrow();
        List<TraitDefinition> rescaledTraits = real.traits().stream()
                .map(trait -> trait.key().equals(RubricTraits.CONTENT)
                        ? new TraitDefinition(trait.key(), trait.displayNameVi(), trait.displayNameEn(),
                        TraitScale.fixed(7), trait.levels(), trait.source(), trait.pearsonPages())
                        : trait)
                .toList();
        TaskRubric rescaled = new TaskRubric(real.taskType(), real.skills(), rescaledTraits, real.gateTraits(),
                real.formRule(), real.objectiveRule(), real.pearsonPages());
        RubricCatalog changedCatalog = spy(new RubricCatalogImpl());
        when(changedCatalog.find(DI)).thenReturn(Optional.of(rescaled));
        ScoringRubricVersion version1 = stored(RubricVersionStatus.ACTIVE, defaultsJson());
        RubricResolver overChangedCatalog = new RubricResolver(versions, changedCatalog, CODEC,
                org.mockito.Mockito.mock(com.pte.assessment.ExamDefinitionService.class));

        assertRubricVersionError(() -> overChangedCatalog.resolve(version1.getPublicId(), DI),
                HttpStatus.INTERNAL_SERVER_ERROR, RubricConstants.RUBRIC_VERSION_SCALE_DRIFT);
    }

    // ---- the scoring entry points: pinned by the exam definition, or ACTIVE for practice ----

    @Test
    void anExamDefinitionsPinnedVersionIsResolvedEvenWhenAnotherVersionIsActive() {
        ScoringRubricVersion pinned = stored(RubricVersionStatus.RETIRED,
                editTask(WE, task -> child(task, RubricConstants.PARAM_TRAIT_WEIGHTS).put(RubricTraits.SPELLING, 1)));
        UUID definition = UUID.randomUUID();
        when(examDefinitions.getByPublicId(definition)).thenReturn(definitionPinnedTo(pinned.getPublicId()));

        ResolvedRubric resolved = resolver.resolveForExamDefinition(definition, WE);

        assertThat(resolved.versionPublicId()).isEqualTo(pinned.getPublicId());
        assertThat(resolved.rubric().weightOf(RubricTraits.SPELLING)).isEqualTo(1);
        verify(versions, never()).findByStatus(any());
    }

    @Test
    void aLegacyExamDefinitionWithoutAPinUsesTheActiveVersion() {
        ScoringRubricVersion active = version(RubricVersionStatus.ACTIVE, defaultsJson());
        when(versions.findByStatus(RubricVersionStatus.ACTIVE)).thenReturn(Optional.of(active));
        UUID definition = UUID.randomUUID();
        when(examDefinitions.getByPublicId(definition)).thenReturn(definitionPinnedTo(null));

        ResolvedRubric resolved = resolver.resolveForExamDefinition(definition, WE);

        assertThat(resolved.versionPublicId()).isEqualTo(active.getPublicId());
    }

    @Test
    void anAnswerWithoutAnExamDefinitionUsesTheActiveVersion() {
        ScoringRubricVersion active = version(RubricVersionStatus.ACTIVE, defaultsJson());
        when(versions.findByStatus(RubricVersionStatus.ACTIVE)).thenReturn(Optional.of(active));

        assertThat(resolver.resolveForExamDefinition(null, WE).versionPublicId()).isEqualTo(active.getPublicId());
        verify(examDefinitions, never()).getByPublicId(any());
    }

    @Test
    void aPinnedVersionThatNoLongerExistsFailsClosedInsteadOfFallingBackToActive() {
        UUID missing = UUID.randomUUID();
        when(versions.findByPublicId(missing)).thenReturn(Optional.empty());
        UUID definition = UUID.randomUUID();
        when(examDefinitions.getByPublicId(definition)).thenReturn(definitionPinnedTo(missing));

        assertRubricVersionError(() -> resolver.resolveForExamDefinition(definition, WE), HttpStatus.NOT_FOUND,
                RubricConstants.RUBRIC_VERSION_NOT_FOUND);
        verify(versions, never()).findByStatus(any());
    }

    @Test
    void resolveActiveVersionCarriesTheActiveVersionId() {
        ScoringRubricVersion active = version(RubricVersionStatus.ACTIVE, defaultsJson());
        when(versions.findByStatus(RubricVersionStatus.ACTIVE)).thenReturn(Optional.of(active));

        ResolvedRubric resolved = resolver.resolveActiveVersion(WE);

        assertThat(resolved.versionPublicId()).isEqualTo(active.getPublicId());
        assertThat(resolved.rubric().task().taskType()).isEqualTo(WE);
    }

    private static ExamDefinitionResponse definitionPinnedTo(UUID rubricVersionPublicId) {
        return new ExamDefinitionResponse(UUID.randomUUID(), "STD", 1, "Standard", "ACTIVE", null, List.of(), null,
                null, null, null, null, null, null, null, null, List.of(), List.of(), rubricVersionPublicId);
    }

    private ScoringRubricVersion stored(RubricVersionStatus status, String json) {
        ScoringRubricVersion version = version(status, json);
        when(versions.findByPublicId(version.getPublicId())).thenReturn(Optional.of(version));
        return version;
    }
}
