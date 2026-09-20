package com.pte.reporting.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptScoreContextView;
import com.pte.reporting.domain.enums.Skill;
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.scoring.ScoringService;
import com.pte.scoring.dto.response.ScoredAnswerView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Rewritten for spec FR-18/19/20 (plans/score-template-exam-generation,
 * Plan A / Phase 5) — replaces the old "every answer contributes equal
 * weight, task→skill via a hardcoded config file" model entirely. Weights
 * and which task types belong to a template come from a mocked {@code
 * ScoreTemplateService.getByPublicId} response; "tested skills" come from
 * a mocked {@code AttemptService.getScoreContext}.
 */
@ExtendWith(MockitoExtension.class)
class ScoreAggregationServiceTest {

    private static final UUID ATTEMPT_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    @Mock
    private ScoringService scoringService;
    @Mock
    private AttemptService attemptService;
    @Mock
    private ScoreTemplateService scoreTemplateService;

    private ScoreAggregationService service;

    @BeforeEach
    void setUp() {
        service = new ScoreAggregationService(scoringService, attemptService, scoreTemplateService);
    }

    private void stubTestedSections(String... sections) {
        when(attemptService.getScoreContext(ATTEMPT_ID))
                .thenReturn(new AttemptScoreContextView(TEMPLATE_ID, Set.of(sections)));
    }

    private void stubTemplate(ScoreTemplateItemResponse... items) {
        when(scoreTemplateService.getByPublicId(TEMPLATE_ID))
                .thenReturn(new ScoreTemplateResponse(TEMPLATE_ID, "APEUNI_V5", 1, "APEUni V5", "ACTIVE", List.of(items)));
    }

    private void stubAnswers(ScoredAnswerView... answers) {
        when(scoringService.getScoredAnswersForAttempt(ATTEMPT_ID, TENANT_ID)).thenReturn(List.of(answers));
    }

    /** One row, only the given skill's weight column set to a non-zero value; every other weight column is 0. */
    private ScoreTemplateItemResponse item(String taskType, BigDecimal overall, BigDecimal speaking,
                                            BigDecimal writing, BigDecimal reading, BigDecimal listening) {
        return new ScoreTemplateItemResponse(taskType, "SPEAKING", 0, 1, 1, 0, 0, "AI_SPEECH",
                overall, speaking, writing, reading, listening);
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }

    @Test
    void aggregate_oneContributingTaskType_matchesFr18ByHand() {
        // weight=10, avgRaw=50 -> 10 + 80*(10*50/100)/10 = 10 + 80*0.5 = 50
        stubTestedSections("READING");
        stubTemplate(item("MC_READING_SINGLE", bd(0), bd(0), bd(0), bd(10), bd(0)));
        stubAnswers(new ScoredAnswerView("MC_READING_SINGLE", 50));

        AttemptScoreSummary summary = service.aggregate(ATTEMPT_ID, TENANT_ID);

        SkillScore reading = summary.skillScores().get(Skill.READING);
        assertThat(reading.sufficientData()).isTrue();
        assertThat(reading.score()).isEqualTo(50);
    }

    @Test
    void aggregate_twoContributingTaskTypes_weightsAverageCorrectly() {
        // item A: weight=20, avgRaw=100 -> contributes 20
        // item B: weight=5,  avgRaw=0   -> contributes 0
        // Σw=25, Σ(w*avg/100)=20 -> ratio=0.8 -> 10+64=74
        stubTestedSections("READING");
        stubTemplate(
                item("MC_READING_SINGLE", bd(0), bd(0), bd(0), bd(20), bd(0)),
                item("MC_READING_MULTIPLE", bd(0), bd(0), bd(0), bd(5), bd(0)));
        stubAnswers(new ScoredAnswerView("MC_READING_SINGLE", 100), new ScoredAnswerView("MC_READING_MULTIPLE", 0));

        AttemptScoreSummary summary = service.aggregate(ATTEMPT_ID, TENANT_ID);

        assertThat(summary.skillScores().get(Skill.READING).score()).isEqualTo(74);
    }

    @Test
    void aggregate_taskTypeInTemplateButZeroScoredAnswers_excludedFromNumeratorAndDenominator() {
        // MC_READING_MULTIPLE has weight 90 but NO scored answer yet — must be
        // dropped entirely, not treated as avgRaw=0 (which would drag the score down).
        stubTestedSections("READING");
        stubTemplate(
                item("MC_READING_SINGLE", bd(0), bd(0), bd(0), bd(10), bd(0)),
                item("MC_READING_MULTIPLE", bd(0), bd(0), bd(0), bd(90), bd(0)));
        stubAnswers(new ScoredAnswerView("MC_READING_SINGLE", 50));

        AttemptScoreSummary summary = service.aggregate(ATTEMPT_ID, TENANT_ID);

        // Same result as the single-item case above (50), NOT dragged toward 10.
        assertThat(summary.skillScores().get(Skill.READING).score()).isEqualTo(50);
    }

    @Test
    void aggregate_skillTestedButNoContributingTaskTypeScoredYet_insufficientDataNoException() {
        // Simulates AI grading still in flight for every weighted task type of this skill.
        stubTestedSections("SPEAKING");
        stubTemplate(item("READ_ALOUD", bd(4), bd(9), bd(0), bd(0), bd(0)));
        stubAnswers(); // nothing scored yet

        AttemptScoreSummary summary = service.aggregate(ATTEMPT_ID, TENANT_ID);

        SkillScore speaking = summary.skillScores().get(Skill.SPEAKING);
        assertThat(speaking.sufficientData()).isFalse();
        assertThat(speaking.score()).isNull();
    }

    @Test
    void aggregate_overallWeightSumZero_insufficientDataEvenWithAllFourSkillsTested() {
        stubTestedSections("SPEAKING", "WRITING", "READING", "LISTENING");
        // overallWeight left at 0 for the only item — every skill has data, but Overall's own column doesn't.
        stubTemplate(item("READ_ALOUD", bd(0), bd(9), bd(0), bd(0), bd(0)));
        stubAnswers(new ScoredAnswerView("READ_ALOUD", 100));

        AttemptScoreSummary summary = service.aggregate(ATTEMPT_ID, TENANT_ID);

        assertThat(summary.overall()).isNotNull();
        assertThat(summary.overall().sufficientData()).isFalse();
    }

    @Test
    void aggregate_roundingBoundary_halfUp() {
        // avg of 8 answers = 405/8 = 50.625 -> 10 + 0.8*50.625 = 50.5 exactly -> rounds to 51.
        stubTestedSections("READING");
        stubTemplate(item("MC_READING_SINGLE", bd(0), bd(0), bd(0), bd(1), bd(0)));
        stubAnswers(
                new ScoredAnswerView("MC_READING_SINGLE", 50), new ScoredAnswerView("MC_READING_SINGLE", 50),
                new ScoredAnswerView("MC_READING_SINGLE", 50), new ScoredAnswerView("MC_READING_SINGLE", 50),
                new ScoredAnswerView("MC_READING_SINGLE", 50), new ScoredAnswerView("MC_READING_SINGLE", 50),
                new ScoredAnswerView("MC_READING_SINGLE", 50), new ScoredAnswerView("MC_READING_SINGLE", 55));

        AttemptScoreSummary summary = service.aggregate(ATTEMPT_ID, TENANT_ID);

        assertThat(summary.skillScores().get(Skill.READING).score()).isEqualTo(51);
    }

    @Test
    void aggregate_partialSkillExam_resultOnlyContainsTestedSkills() {
        stubTestedSections("SPEAKING", "READING");
        stubTemplate(
                // REPEAT_SENTENCE also carries a LISTENING weight in the real V5 table —
                // LISTENING must NOT appear in the result even though this row has data for it.
                item("REPEAT_SENTENCE", bd(7), bd(16), bd(0), bd(0), bd(17)),
                item("MC_READING_SINGLE", bd(0), bd(0), bd(0), bd(3), bd(0)));
        stubAnswers(new ScoredAnswerView("REPEAT_SENTENCE", 80), new ScoredAnswerView("MC_READING_SINGLE", 60));

        AttemptScoreSummary summary = service.aggregate(ATTEMPT_ID, TENANT_ID);

        assertThat(summary.skillScores()).containsOnlyKeys(Skill.SPEAKING, Skill.READING);
    }

    @Test
    void aggregate_allFourSkillsTested_overallUsesOverallWeight() {
        stubTestedSections("SPEAKING", "WRITING", "READING", "LISTENING");
        stubTemplate(item("READ_ALOUD", bd(4), bd(9), bd(0), bd(0), bd(0)));
        stubAnswers(new ScoredAnswerView("READ_ALOUD", 100));

        AttemptScoreSummary summary = service.aggregate(ATTEMPT_ID, TENANT_ID);

        // weight=4, avgRaw=100 -> 10 + 80*(4*100/100)/4 = 10 + 80 = 90
        assertThat(summary.overall()).isNotNull();
        assertThat(summary.overall().sufficientData()).isTrue();
        assertThat(summary.overall().score()).isEqualTo(90);
    }

    @Test
    void aggregate_fewerThanFourSkillsTested_overallIsNull() {
        stubTestedSections("SPEAKING", "READING", "LISTENING");
        stubTemplate(item("READ_ALOUD", bd(4), bd(9), bd(0), bd(0), bd(0)));
        stubAnswers(new ScoredAnswerView("READ_ALOUD", 100));

        AttemptScoreSummary summary = service.aggregate(ATTEMPT_ID, TENANT_ID);

        assertThat(summary.overall()).isNull();
    }

    /** Hand-verified against the real APEUni V5 seed table (Phase 1): RA (overall 4, speaking 9) + DI (overall 15, speaking 31). */
    @Test
    void aggregate_realV5Numbers_speakingSkillMatchesHandCalculation() {
        stubTestedSections("SPEAKING");
        stubTemplate(
                item("READ_ALOUD", bd(4), bd(9), bd(0), bd(0), bd(0)),
                item("DESCRIBE_IMAGE", bd(15), bd(31), bd(0), bd(0), bd(0)));
        stubAnswers(new ScoredAnswerView("READ_ALOUD", 100), new ScoredAnswerView("DESCRIBE_IMAGE", 50));

        AttemptScoreSummary summary = service.aggregate(ATTEMPT_ID, TENANT_ID);

        // Σw = 9+31 = 40; Σ(w*avg/100) = 9*1 + 31*0.5 = 9+15.5 = 24.5; ratio=0.6125
        // score = 10 + 80*0.6125 = 10 + 49 = 59
        assertThat(summary.skillScores().get(Skill.SPEAKING).score()).isEqualTo(59);
    }

    @Test
    void aggregate_oneTaskTypeContributesToTwoSkills_computesEachIndependently() {
        stubTestedSections("SPEAKING", "LISTENING");
        stubTemplate(item("REPEAT_SENTENCE", bd(7), bd(16), bd(0), bd(0), bd(17)));
        stubAnswers(new ScoredAnswerView("REPEAT_SENTENCE", 100));

        AttemptScoreSummary summary = service.aggregate(ATTEMPT_ID, TENANT_ID);

        // Speaking: weight 16, avgRaw 100 -> 10+80 = 90. Listening: weight 17, avgRaw 100 -> 10+80 = 90.
        assertThat(summary.skillScores().get(Skill.SPEAKING).score()).isEqualTo(90);
        assertThat(summary.skillScores().get(Skill.LISTENING).score()).isEqualTo(90);
    }

}
