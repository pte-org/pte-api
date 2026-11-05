package com.pte.scoring;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.exception.RubricVersionException;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.internal.rubric.service.RubricResolver;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.FormBand;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.rubric.RubricParameters;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The maximum of each trait of a stored rubric version, for showing "achieved / max" next to Pearson scores. */
class RubricScaleServiceTest {

    private static final RubricCatalog CATALOG = new RubricCatalogImpl();
    private static final String WE = ScoringConstants.TASK_TYPE_WRITE_ESSAY;
    private static final UUID VERSION = UUID.randomUUID();

    private final RubricResolver resolver = mock(RubricResolver.class);
    private final RubricScaleService service = new RubricScaleService(resolver);

    @Test
    void writeEssayMaximaAreThePearsonScalesWithFormFromTheVersionsBands() {
        when(resolver.resolve(VERSION, WE)).thenReturn(defaults(WE));

        assertThat(service.traitMaxima(VERSION, WE)).contains(Map.of("CONTENT", 6, "FORM", 2,
                "DEVELOPMENT_STRUCTURE_COHERENCE", 6, "GRAMMAR", 2, "GENERAL_LINGUISTIC_RANGE", 6,
                "VOCABULARY_RANGE", 2, "SPELLING", 2));
    }

    @Test
    void editedFormBandsChangeTheFormMaximum() {
        RubricParameters defaults = CATALOG.defaultParameters(WE);
        EffectiveRubric edited = new EffectiveRubric(CATALOG.find(WE).orElseThrow(), new RubricParameters(
                defaults.traitWeights(), List.of(new FormBand(120, 380, 1)), defaults.gateEnabled(), null));
        when(resolver.resolve(VERSION, WE)).thenReturn(edited);

        assertThat(service.traitMaxima(VERSION, WE).orElseThrow()).containsEntry("FORM", 1);
    }

    @Test
    void anUnknownVersionShowsNoMaximaInsteadOfFailingTheRead() {
        UUID unknown = UUID.randomUUID();
        when(resolver.resolve(unknown, WE)).thenThrow(
                new RubricVersionException(HttpStatus.NOT_FOUND, RubricConstants.RUBRIC_VERSION_NOT_FOUND));

        assertThat(service.traitMaxima(unknown, WE)).isEmpty();
        assertThat(service.traitMaxima(null, WE)).isEmpty();
        assertThat(service.traitMaxima(VERSION, null)).isEmpty();
    }

    @Test
    void aVersionsMaximaAreLookedUpOnceBecauseStoredVersionsAreImmutable() {
        when(resolver.resolve(VERSION, WE)).thenReturn(defaults(WE));

        Optional<Map<String, Integer>> first = service.traitMaxima(VERSION, WE);
        Optional<Map<String, Integer>> second = service.traitMaxima(VERSION, WE);

        assertThat(second).isEqualTo(first);
        verify(resolver, times(1)).resolve(VERSION, WE);
    }

    private static EffectiveRubric defaults(String taskType) {
        return new EffectiveRubric(CATALOG.find(taskType).orElseThrow(), CATALOG.defaultParameters(taskType));
    }
}
