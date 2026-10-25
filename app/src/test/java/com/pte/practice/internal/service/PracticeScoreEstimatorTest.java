package com.pte.practice.internal.service;

import com.pte.practice.internal.dto.response.PracticeEstimatedScoresResponse;
import com.pte.practice.internal.service.PracticeScoreEstimator.ItemOutcome;
import com.pte.practice.internal.service.PracticeScoreEstimator.ScoredItem;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeScoreEstimatorTest {

    private final PracticeScoreEstimator estimator = new PracticeScoreEstimator();

    private static ScoreTemplateItemResponse template(String type, String section, double overall, double speaking,
            double reading) {
        return new ScoreTemplateItemResponse(type, section, 1, 1, 1, 0, 0, "AI", BigDecimal.valueOf(overall),
                BigDecimal.valueOf(speaking), BigDecimal.ZERO, BigDecimal.valueOf(reading), BigDecimal.ZERO);
    }

    private final List<ScoreTemplateItemResponse> templateItems = List.of(
            template("READ_ALOUD", "SPEAKING", 2, 3, 0),
            template("REPEAT_SENTENCE", "SPEAKING", 1, 1, 0),
            template("MC_READING_SINGLE", "READING", 1, 0, 1));

    @Test
    void fullMarksGiveNinetyAndZeroGivesTen() {
        var full = estimator.estimate(List.of(ScoredItem.scored("READ_ALOUD", 100),
                ScoredItem.scored("REPEAT_SENTENCE", 100), ScoredItem.scored("MC_READING_SINGLE", 100)), templateItems);
        assertThat(full.overall()).isEqualTo(90);
        assertThat(full.speaking()).isEqualTo(90);

        var zero = estimator.estimate(List.of(ScoredItem.scored("READ_ALOUD", 0),
                ScoredItem.scored("REPEAT_SENTENCE", 0), ScoredItem.scored("MC_READING_SINGLE", 0)), templateItems);
        assertThat(zero.overall()).isEqualTo(10);
    }

    @Test
    void weightsDecideTheSkillScoreAndTaskTypesAreAveraged() {
        // Speaking: READ_ALOUD avg (100+50)/2 = 75 with weight 3, REPEAT_SENTENCE 0 with weight 1
        // ratio = (3*0.75 + 1*0) / 4 = 0.5625 -> 10 + 80 * 0.5625 = 55
        var result = estimator.estimate(List.of(ScoredItem.scored("READ_ALOUD", 100), ScoredItem.scored("READ_ALOUD", 50),
                ScoredItem.scored("REPEAT_SENTENCE", 0)), templateItems);
        assertThat(result.speaking()).isEqualTo(55);
        assertThat(result.reading()).isNull();
    }

    @Test
    void skippedItemsCountAsZeroAndAreReported() {
        var result = estimator.estimate(List.of(ScoredItem.scored("READ_ALOUD", 100),
                ScoredItem.of("READ_ALOUD", ItemOutcome.MISSING)), templateItems);
        assertThat(result.skippedItemCount()).isEqualTo(1);
        // avg 50 -> 10 + 80 * 0.5 = 50 for the only weighted type
        assertThat(result.speaking()).isEqualTo(50);
    }

    @Test
    void scoringItemsMakeTheEstimatePendingAndFailedOnesAreExcluded() {
        PracticeEstimatedScoresResponse result = estimator.estimate(List.of(ScoredItem.scored("READ_ALOUD", 80),
                ScoredItem.of("REPEAT_SENTENCE", ItemOutcome.SCORING), ScoredItem.of("MC_READING_SINGLE", ItemOutcome.FAILED)),
                templateItems);
        assertThat(result.pending()).isTrue();
        assertThat(result.excludedItemCount()).isEqualTo(1);
        assertThat(result.reading()).isNull();
        assertThat(result.speaking()).isEqualTo(74);
    }
}
