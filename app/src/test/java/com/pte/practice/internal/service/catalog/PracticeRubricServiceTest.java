package com.pte.practice.internal.service.catalog;

import com.pte.practice.internal.dto.response.PracticeRubricResponse;
import com.pte.scoring.RubricCriteriaService;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.RubricCatalog;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PracticeRubricServiceTest {

    private static final RubricCatalog CATALOG = new RubricCatalogImpl();
    private final RubricCriteriaService criteria = mock(RubricCriteriaService.class);
    private final PracticeRubricService service = new PracticeRubricService(criteria);

    @Test
    void listsEachTraitWithItsScaleAndLevelDescriptions() {
        when(criteria.activeRubric("DESCRIBE_IMAGE")).thenReturn(Optional.of(new EffectiveRubric(
                CATALOG.find("DESCRIBE_IMAGE").orElseThrow(), CATALOG.defaultParameters("DESCRIBE_IMAGE"))));

        PracticeRubricResponse response = service.rubricOf("DESCRIBE_IMAGE");

        assertThat(response.traits()).isNotEmpty();
        assertThat(response.traits()).allSatisfy(trait -> {
            assertThat(trait.displayNameVi()).isNotBlank();
            assertThat(trait.levels()).isNotEmpty();
        });
    }

    @Test
    void aPerWordTraitHasNoFixedMaximum() {
        when(criteria.activeRubric("READ_ALOUD")).thenReturn(Optional.of(new EffectiveRubric(
                CATALOG.find("READ_ALOUD").orElseThrow(), CATALOG.defaultParameters("READ_ALOUD"))));

        PracticeRubricResponse response = service.rubricOf("READ_ALOUD");

        assertThat(response.traits()).anySatisfy(trait -> assertThat(trait.maxScore()).isNull());
        assertThat(response.traits()).anySatisfy(trait -> assertThat(trait.maxScore()).isPositive());
    }

    @Test
    void anObjectiveTaskHasNoCriteria() {
        when(criteria.activeRubric("MC_READING_SINGLE")).thenReturn(Optional.empty());

        assertThat(service.rubricOf("MC_READING_SINGLE").traits()).isEmpty();
    }
}
