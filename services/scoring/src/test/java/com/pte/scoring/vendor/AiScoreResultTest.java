package com.pte.scoring.vendor;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiScoreResultTest {

    @Test
    void result_validatesAndDefensivelyCopiesScores() {
        Map<String, Integer> scores = new HashMap<>();
        scores.put("GRAMMAR", 80);

        AiScoreResult result = new AiScoreResult(82, scores, null);
        scores.put("GRAMMAR", 0);

        assertThat(result.rawScore()).isEqualTo(82);
        assertThat(result.subScores()).containsEntry("GRAMMAR", 80);
        assertThat(result.feedback()).isEmpty();
    }

    @Test
    void result_rejectsOutOfRangeRawAndSubScores() {
        assertThatThrownBy(() -> new AiScoreResult(101, Map.of(), "feedback"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiScoreResult(80, Map.of("GRAMMAR", -1), "feedback"))
                .isInstanceOf(IllegalArgumentException.class);
        Map<String, Integer> nullSubScore = new HashMap<>();
        nullSubScore.put("GRAMMAR", null);
        assertThatThrownBy(() -> new AiScoreResult(80, nullSubScore, "feedback"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
