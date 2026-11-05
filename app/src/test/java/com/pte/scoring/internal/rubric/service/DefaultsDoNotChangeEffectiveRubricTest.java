package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.FormBand;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.rubric.RubricParameters;
import com.pte.scoring.rubric.RubricScorer;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitScoreSet;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.CODEC;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.seedJson;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plan HIGH-3: changing the code defaults never changes the effective rubric of an existing version. Version 1 is
 * resolved once with the real catalog and once with a catalog whose defaults are different; nothing changes.
 * Plain unit test: no Spring context.
 */
class DefaultsDoNotChangeEffectiveRubricTest {

    private final RubricCatalog realCatalog = new RubricCatalogImpl();
    private final RubricCatalog changedDefaults = new ChangedDefaultsCatalog(realCatalog);

    @Test
    void theStubCatalogReallyHasDifferentDefaults() {
        assertThat(changedDefaults.defaultParameters("DESCRIBE_IMAGE"))
                .isNotEqualTo(realCatalog.defaultParameters("DESCRIBE_IMAGE"));
        assertThat(changedDefaults.defaultParameters("WRITE_ESSAY").formBands())
                .isNotEqualTo(realCatalog.defaultParameters("WRITE_ESSAY").formBands());
    }

    @Test
    void version1HasTheSameEffectiveRubricForAll22TaskTypesWhateverTheDefaults() {
        ScoringRubricVersion version1 = version(RubricVersionStatus.ACTIVE, seedJson());

        Map<String, EffectiveRubric> before = resolverOver(realCatalog, version1).effectiveRubrics(version1);
        Map<String, EffectiveRubric> after = resolverOver(changedDefaults, version1).effectiveRubrics(version1);

        assertThat(after).hasSize(22).isEqualTo(before);
    }

    @Test
    void itemScoresOfVersion1DoNotMoveWhenDefaultsChange() {
        ScoringRubricVersion version1 = version(RubricVersionStatus.ACTIVE, seedJson());
        TraitScoreSet essay = new TraitScoreSet(Map.of(RubricTraits.CONTENT, 4, RubricTraits.FORM, 2,
                RubricTraits.DEVELOPMENT_STRUCTURE_COHERENCE, 3, RubricTraits.GRAMMAR, 1,
                RubricTraits.GENERAL_LINGUISTIC_RANGE, 5, RubricTraits.VOCABULARY_RANGE, 2, RubricTraits.SPELLING, 1),
                null);

        int before = RubricScorer.score(resolverOver(realCatalog, version1).effectiveFor(version1, "WRITE_ESSAY"),
                essay).itemScore();
        int after = RubricScorer.score(resolverOver(changedDefaults, version1).effectiveFor(version1, "WRITE_ESSAY"),
                essay).itemScore();

        assertThat(after).isEqualTo(before);
    }

    private static RubricResolver resolverOver(RubricCatalog catalog, ScoringRubricVersion version) {
        ScoringRubricVersionRepository versions = mock(ScoringRubricVersionRepository.class);
        when(versions.findByPublicId(version.getPublicId())).thenReturn(Optional.of(version));
        return new RubricResolver(versions, catalog, CODEC,
                org.mockito.Mockito.mock(com.pte.assessment.ExamDefinitionService.class));
    }

    /** Same structure and descriptions as the real catalog; every default weight halved-plus-one, Form bands moved. */
    private record ChangedDefaultsCatalog(RubricCatalog delegate) implements RubricCatalog {

        @Override
        public Optional<TaskRubric> find(String taskType) {
            return delegate.find(taskType);
        }

        @Override
        public List<TaskRubric> all() {
            return delegate.all();
        }

        @Override
        public int catalogVersion() {
            return delegate.catalogVersion() + 1;
        }

        @Override
        public RubricParameters defaultParameters(String taskType) {
            RubricParameters real = delegate.defaultParameters(taskType);
            Map<String, Integer> weights = new HashMap<>();
            real.traitWeights().forEach((key, weight) -> weights.put(key, weight / 2 + 1));
            List<FormBand> bands = real.formBands() == null ? null : List.of(new FormBand(1, 1000, 2));
            return new RubricParameters(weights, bands, real.gateEnabled(), "2");
        }
    }
}
