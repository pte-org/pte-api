package com.pte.scoring.internal.service.objective;

import com.pte.attempt.dto.response.SubmittedAnswerView;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceAnswerScorerListeningBlanksTest {

    private final ReferenceAnswerScorer scorer = new ReferenceAnswerScorer(JsonMapper.builder().build());

    private static SubmittedAnswerView answer(String reference, String payload) {
        return new SubmittedAnswerView(null, null, null, null, null, null, "FILL_IN_THE_BLANKS_TYPE_IN",
                payload, reference, null, false, null, null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void anAnswerKeyWrittenWithCapitalsOrExtraSpacesStillMatchesTheLowercasedSubmission() {
        assertThat(scorer.scoreListeningFillBlanks(answer("[\"London\", \" Hydrogen \"]", "london,hydrogen"))).isEqualTo(100);
        assertThat(scorer.scoreListeningFillBlanks(answer("[\"Ice  Cream\"]", "ice cream"))).isEqualTo(100);
    }

    @Test
    void wrongOrMissingGapsStillLosePointsAndAMalformedKeyStaysAtZero() {
        assertThat(scorer.scoreListeningFillBlanks(answer("[\"London\", \"Paris\"]", "london,rome"))).isEqualTo(50);
        assertThat(scorer.scoreListeningFillBlanks(answer("London, Paris", "london,paris"))).isZero();
    }
}
