package com.pte.scoring.internal.service.review;

import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.support.EvaluationFixtures;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Only PEARSON_V1 criteria yield trait scores; the word count comes from the stored detail (Read Aloud's N). */
class ReportTraitReaderTest {

    private final ReportTraitReader reader = new ReportTraitReader(JsonMapper.builder().build());

    @Test
    void pearsonTraitsTheRowsRubricVersionAndTheStoredWordCountAreRead() {
        UUID version = UUID.randomUUID();
        AiEvaluation answer = EvaluationFixtures.ai("READ_ALOUD", ScoringMethod.AI_SPEECH);
        answer.recordDetail("{\"scale\":\"PEARSON_V1\",\"rubricVersion\":\"" + version + "\",\"traits\":"
                + "{\"CONTENT\":24,\"ORAL_FLUENCY\":3,\"PRONUNCIATION\":4}}", null, null,
                "{\"wordCount\":30,\"readAloudContent\":{\"wordCount\":30}}", false, version);

        ReportTraitReader.StoredTraits traits = reader.read(answer);

        assertThat(traits.traits()).isEqualTo(Map.of("CONTENT", 24, "ORAL_FLUENCY", 3, "PRONUNCIATION", 4));
        assertThat(traits.rubricVersionPublicId()).isEqualTo(version);
        assertThat(traits.wordCount()).isEqualTo(30);
    }

    @Test
    void theVersionInsideTheCriteriaIsUsedWhenTheColumnIsEmpty() {
        UUID version = UUID.randomUUID();
        AiEvaluation answer = EvaluationFixtures.ai("DESCRIBE_IMAGE", ScoringMethod.AI_SPEECH);
        answer.recordDetail("{\"scale\":\"PEARSON_V1\",\"rubricVersion\":\"" + version + "\",\"traits\":"
                + "{\"CONTENT\":4}}", null, null, "not json", false, null);

        ReportTraitReader.StoredTraits traits = reader.read(answer);

        assertThat(traits.rubricVersionPublicId()).isEqualTo(version);
        assertThat(traits.wordCount()).isNull();
    }

    @Test
    void legacyAndMissingCriteriaYieldNoTraits() {
        AiEvaluation legacy = EvaluationFixtures.ai("DESCRIBE_IMAGE", ScoringMethod.AI_SPEECH);
        legacy.recordDetail("{\"CONTENT\":80,\"FLUENCY\":70}", null, null, "{\"wordCount\":12}", false);
        AiEvaluation objective = EvaluationFixtures.ai("MC_READING_SINGLE", ScoringMethod.OBJECTIVE);

        for (AiEvaluation answer : new AiEvaluation[] {legacy, objective}) {
            ReportTraitReader.StoredTraits traits = reader.read(answer);
            assertThat(traits.traits()).isEmpty();
            assertThat(traits.rubricVersionPublicId()).isNull();
            assertThat(traits.wordCount()).isNull();
        }
    }
}
