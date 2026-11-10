package com.pte.scoring;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code answer_evaluations.criteria_scores_json} (and the practice sub-score column) holds either the legacy flat
 * 0-100 map or, from the rubric writing scorers on, {"scale":"PEARSON_V1","rubricVersion":..,"traits":{..}}.
 * Readers tell them apart by {@code scale}; old rows are never converted.
 */
class CriteriaScoresTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID VERSION = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    @Test
    void pearsonScoresAreWrittenWithTheScaleTheRubricVersionAndSortedTraits() {
        Map<String, Integer> traits = new LinkedHashMap<>();
        traits.put("VOCABULARY", 1);
        traits.put("CONTENT", 3);

        String json = CriteriaScores.pearson(VERSION, traits).toJson(JSON);

        assertThat(json).isEqualTo("{\"scale\":\"PEARSON_V1\",\"rubricVersion\":\"" + VERSION
                + "\",\"traits\":{\"CONTENT\":3,\"VOCABULARY\":1}}");
    }

    @Test
    void legacyScoresAreWrittenAsTheSortedFlatMapTheyAlwaysWere() {
        assertThat(CriteriaScores.legacy(Map.of("speaking", 70, "content", 80)).toJson(JSON))
                .isEqualTo("{\"content\":80,\"speaking\":70}");
    }

    @Test
    void aPearsonRowIsReadBackWithItsScaleAndVersion() {
        CriteriaScores read = CriteriaScores.read(JSON, CriteriaScores.pearson(VERSION,
                Map.of("CONTENT", 3, "FORM", 1)).toJson(JSON));

        assertThat(read.isPearson()).isTrue();
        assertThat(read.scale()).isEqualTo(CriteriaScores.SCALE_PEARSON_V1);
        assertThat(read.rubricVersion()).isEqualTo(VERSION);
        assertThat(read.traits()).containsExactlyInAnyOrderEntriesOf(Map.of("CONTENT", 3, "FORM", 1));
    }

    @Test
    void anOldFlatRowIsReadAsLegacyZeroToHundredScores() {
        CriteriaScores read = CriteriaScores.read(JSON, "{\"CONTENT\":80,\"FLUENCY\":64,\"note\":\"x\"}");

        assertThat(read.isPearson()).isFalse();
        assertThat(read.scale()).isNull();
        assertThat(read.rubricVersion()).isNull();
        assertThat(read.traits()).containsExactlyInAnyOrderEntriesOf(Map.of("CONTENT", 80, "FLUENCY", 64));
    }

    @Test
    void anythingUnreadableOrOfAnUnknownScaleShowsNoScores() {
        for (String stored : new String[]{null, "", "  ", "not json", "[1,2]", "{\"scale\":\"OTHER\",\"traits\":{"
                + "\"CONTENT\":3}}", "{\"scale\":\"PEARSON_V1\",\"traits\":[]}"}) {
            CriteriaScores read = CriteriaScores.read(JSON, stored);

            assertThat(read.traits()).as(String.valueOf(stored)).isEmpty();
            assertThat(read.isPearson()).as(String.valueOf(stored)).isFalse();
        }
    }

    @Test
    void aPearsonRowKeepsOnlyWholeNumberTraits() {
        CriteriaScores read = CriteriaScores.read(JSON,
                "{\"scale\":\"PEARSON_V1\",\"rubricVersion\":\"bad-uuid\",\"traits\":{\"CONTENT\":3,\"X\":2.5,"
                        + "\"Y\":\"4\"}}");

        assertThat(read.isPearson()).isTrue();
        assertThat(read.rubricVersion()).isNull();
        assertThat(read.traits()).containsExactly(Map.entry("CONTENT", 3));
    }
}
