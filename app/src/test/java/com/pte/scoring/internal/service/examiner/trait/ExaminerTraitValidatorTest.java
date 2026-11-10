package com.pte.scoring.internal.service.examiner.trait;

import com.pte.scoring.internal.rubric.service.RubricVersionFixtures;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.RubricScorer;
import com.pte.scoring.rubric.TraitScoreSet;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ExaminerTraitValidatorTest {
    private final ExaminerTraitValidator validator = new ExaminerTraitValidator();
    private final EffectiveRubric rubric = new EffectiveRubric(RubricVersionFixtures.CATALOG.find("DESCRIBE_IMAGE").orElseThrow(),
            RubricVersionFixtures.CATALOG.defaultParameters("DESCRIBE_IMAGE"));
    private final Map<String, Integer> ai = Map.of("CONTENT", 4, "PRONUNCIATION", 3, "ORAL_FLUENCY", 4);

    @Test void machineTraitsComeFromTheSameAnswerAndContentGateUsesTheScorer() {
        var traits = validator.validate(rubric, Map.of("CONTENT", 0), ai, true, null, null);
        assertThat(traits).containsEntry("PRONUNCIATION", 3).containsEntry("ORAL_FLUENCY", 4);
        assertThat(RubricScorer.score(rubric, new TraitScoreSet(traits, null)).itemScore()).isZero();
    }
    @Test void aLegacyTotalOnlyPayloadHasItsOwnErrorCode() {
        assertThatThrownBy(() -> validator.validate(rubric, null, ai, true, null, null))
                .hasMessage("EXAMINER_TRAITS_REQUIRED");
    }
    @Test void azureTraitsDefaultToTheMachineBandUnlessTheExaminerOverridesThem() {
        assertThat(validator.validate(rubric, Map.of("CONTENT", 4, "PRONUNCIATION", 1), ai, true, null, null))
                .containsEntry("PRONUNCIATION", 1);
        assertThat(validator.validate(rubric, Map.of("CONTENT", 4, "PRONUNCIATION", 1, "ORAL_FLUENCY", 2), ai, false,
                null, null)).containsEntry("ORAL_FLUENCY", 2);
        assertThatThrownBy(() -> validator.validate(rubric, Map.of("CONTENT", 4), ai, false, null, null))
                .hasMessage("EXAMINER_AI_TRAITS_UNAVAILABLE");
    }
}
