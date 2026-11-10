package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.rubric.RubricParameters;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.TaskRubric;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.CATALOG;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.CODEC;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.JSON;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.seedJson;
import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.v32Sql;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The version-1 seed in V32 equals RubricCatalog.defaultParameters for all 22 task types. If this fails after a
 * catalog change, do NOT edit the V32 seed: create a new rubric version instead (V32 is already applied).
 */
class RubricSeedParityTest {

    private static final Pattern UUID_LITERAL =
            Pattern.compile("'([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})'");

    @Test
    void theSeedHoldsExactlyThe22CatalogTaskTypes() {
        StoredRubricParameters seed = CODEC.decode(seedJson());

        assertThat(seed.taskTypes()).hasSize(22);
        assertThat(seed.taskTypes().keySet())
                .containsExactlyInAnyOrderElementsOf(CATALOG.all().stream().map(TaskRubric::taskType).toList());
        assertThat(seed.azureBandMappingVersion()).isEqualTo(RubricConstants.AZURE_BAND_MAPPING_VERSION_1);
    }

    @Test
    void seedDollarQuoteNeverLooksLikeAFlywayPlaceholder() {
        assertThat(v32Sql()).doesNotContain("${");
    }

    @Test
    void everyTaskTypeMatchesTheCodeDefaults() {
        StoredRubricParameters seed = CODEC.decode(seedJson());

        for (TaskRubric task : CATALOG.all()) {
            RubricParameters stored = seed.taskTypes().get(task.taskType());
            RubricParameters defaults = CATALOG.defaultParameters(task.taskType());
            assertThat(stored.traitWeights()).as(task.taskType() + " weights").isEqualTo(defaults.traitWeights());
            assertThat(stored.formBands()).as(task.taskType() + " Form bands").isEqualTo(defaults.formBands());
            assertThat(stored.gateEnabled()).as(task.taskType() + " gates").isEqualTo(defaults.gateEnabled());
        }
    }

    @Test
    void everyTaskTypeStoresTheCatalogTraitScalesIncludingThePerWordMarker() {
        StoredRubricParameters seed = CODEC.decode(seedJson());

        for (TaskRubric task : CATALOG.all()) {
            assertThat(seed.traitScales().get(task.taskType())).as(task.taskType() + " scales")
                    .isEqualTo(StoredRubricParameters.scalesOf(task));
        }
        assertThat(seed.traitScales().get("READ_ALOUD").get(RubricTraits.CONTENT).isPerWord()).isTrue();
        assertThat(seedJson()).contains("\"" + RubricConstants.PARAM_SCALE_PER_WORD + "\"");
    }

    @Test
    void theSeedIsExactlyWhatTheCodecWritesForTheCatalogDefaults() {
        assertThat(JSON.readTree(seedJson())).isEqualTo(JSON.readTree(RubricVersionFixtures.defaultsJson()));
    }

    @Test
    void theSeedPassesTheSameValidatorAsAnyNewVersion() {
        new RubricParametersValidator(CATALOG, CODEC).validate(seedJson(), CATALOG.catalogVersion());
    }

    @Test
    void theBackfillPinsExamDefinitionsToTheSeededVersion() {
        Matcher uuids = UUID_LITERAL.matcher(v32Sql());
        Set<String> distinct = new HashSet<>();
        while (uuids.find()) {
            distinct.add(uuids.group(1));
        }

        assertThat(distinct).as("seed public_id and backfill value must be the same UUID").hasSize(1);
        assertThat(v32Sql()).contains("UPDATE exam_definitions").contains("'ACTIVE'");
    }
}
