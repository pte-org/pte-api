package com.pte.scoring.service;

import com.pte.scoring.constant.ScoringConstants;
import com.pte.scoring.domain.ScoringAnswer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers Phase 7's 4 new objective evaluators: MC_READING_MULTIPLE (negative
 * marking), RE_ORDER_PARAGRAPHS (adjacent-pair partial credit), and the
 * shared FILL_BLANKS_READING / FILL_BLANKS_READING_WRITING evaluator
 * (positional payload, including the HIGH-risk trailing-empty-entry case).
 */
class ObjectiveScoringServiceTest {

    private final ObjectiveScoringService service = new ObjectiveScoringService(JsonMapper.builder().build());

    private ScoringAnswer answer(String taskType, String optionsJson, String payload) {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setTaskType(taskType);
        answer.setOptionsJson(optionsJson);
        answer.setPayload(payload);
        return answer;
    }

    // ---- supports() ----

    @Test
    void supports_allFiveObjectiveTaskTypes() {
        assertThat(service.supports(ScoringConstants.TASK_TYPE_MC_READING_SINGLE)).isTrue();
        assertThat(service.supports(ScoringConstants.TASK_TYPE_MC_READING_MULTIPLE)).isTrue();
        assertThat(service.supports(ScoringConstants.TASK_TYPE_RE_ORDER_PARAGRAPHS)).isTrue();
        assertThat(service.supports(ScoringConstants.TASK_TYPE_FILL_BLANKS_READING)).isTrue();
        assertThat(service.supports(ScoringConstants.TASK_TYPE_FILL_BLANKS_READING_WRITING)).isTrue();
        assertThat(service.supports(ScoringConstants.TASK_TYPE_WRITE_ESSAY)).isFalse();
    }

    // ---- MC_READING_SINGLE (regression — scoreSingleChoice was refactored to share parseOptions()) ----

    private static final String MC_SINGLE_OPTIONS =
            "[{\"text\":\"London\",\"correct\":false,\"orderIndex\":0},"
                    + "{\"text\":\"Paris\",\"correct\":true,\"orderIndex\":1}]";

    @Test
    void mcReadingSingle_correctSelection_scoresFullMarks() {
        int score = service.score(answer(ScoringConstants.TASK_TYPE_MC_READING_SINGLE, MC_SINGLE_OPTIONS, "1"));
        assertThat(score).isEqualTo(100);
    }

    @Test
    void mcReadingSingle_incorrectSelection_scoresZero() {
        int score = service.score(answer(ScoringConstants.TASK_TYPE_MC_READING_SINGLE, MC_SINGLE_OPTIONS, "0"));
        assertThat(score).isZero();
    }

    // ---- MC_READING_MULTIPLE ----

    private static final String MC_MULTIPLE_OPTIONS =
            "[{\"text\":\"a\",\"correct\":true,\"orderIndex\":0},"
                    + "{\"text\":\"b\",\"correct\":true,\"orderIndex\":1},"
                    + "{\"text\":\"c\",\"correct\":false,\"orderIndex\":2},"
                    + "{\"text\":\"d\",\"correct\":false,\"orderIndex\":3}]";

    @Test
    void mcReadingMultiple_allCorrectSelected_scoresFullMarks() {
        int score = service.score(answer(ScoringConstants.TASK_TYPE_MC_READING_MULTIPLE, MC_MULTIPLE_OPTIONS, "0,1"));
        assertThat(score).isEqualTo(100);
    }

    @Test
    void mcReadingMultiple_allIncorrectSelected_floorsAtZero_neverNegative() {
        int score = service.score(answer(ScoringConstants.TASK_TYPE_MC_READING_MULTIPLE, MC_MULTIPLE_OPTIONS, "2,3"));
        assertThat(score).isZero();
    }

    @Test
    void mcReadingMultiple_partiallyCorrect_appliesNegativeMarking() {
        // 1 correct (a) + 1 incorrect (c) selected: points = 1 - 1 = 0 of 2 possible.
        int score = service.score(answer(ScoringConstants.TASK_TYPE_MC_READING_MULTIPLE, MC_MULTIPLE_OPTIONS, "0,2"));
        assertThat(score).isZero();
    }

    @Test
    void mcReadingMultiple_oneCorrectNoIncorrect_scoresHalfOfTwoCorrect() {
        int score = service.score(answer(ScoringConstants.TASK_TYPE_MC_READING_MULTIPLE, MC_MULTIPLE_OPTIONS, "0"));
        assertThat(score).isEqualTo(50);
    }

    @Test
    void mcReadingMultiple_emptySelection_scoresZero() {
        int score = service.score(answer(ScoringConstants.TASK_TYPE_MC_READING_MULTIPLE, MC_MULTIPLE_OPTIONS, ""));
        assertThat(score).isZero();
    }

    // ---- RE_ORDER_PARAGRAPHS ----

    // 4 paragraphs; correct order is identity 0,1,2,3.
    private static final String REORDER_OPTIONS =
            "[{\"text\":\"p0\",\"correct\":false,\"orderIndex\":0},"
                    + "{\"text\":\"p1\",\"correct\":false,\"orderIndex\":1},"
                    + "{\"text\":\"p2\",\"correct\":false,\"orderIndex\":2},"
                    + "{\"text\":\"p3\",\"correct\":false,\"orderIndex\":3}]";

    @Test
    void reOrderParagraphs_fullyCorrectSequence_scoresFullMarks() {
        int score = service.score(answer(ScoringConstants.TASK_TYPE_RE_ORDER_PARAGRAPHS, REORDER_OPTIONS, "0,1,2,3"));
        assertThat(score).isEqualTo(100);
    }

    @Test
    void reOrderParagraphs_fullyReversedSequence_scoresZeroAdjacentPairs() {
        int score = service.score(answer(ScoringConstants.TASK_TYPE_RE_ORDER_PARAGRAPHS, REORDER_OPTIONS, "3,2,1,0"));
        assertThat(score).isZero();
    }

    @Test
    void reOrderParagraphs_partiallyCorrectAdjacentPairs_handVerifiedCount() {
        // Submitted: 0,1,3,2 — pairs (0,1)=correct, (1,3)=wrong, (3,2)=wrong.
        // 1 of 3 total pairs correct -> round(100/3) = 33.
        int score = service.score(answer(ScoringConstants.TASK_TYPE_RE_ORDER_PARAGRAPHS, REORDER_OPTIONS, "0,1,3,2"));
        assertThat(score).isEqualTo(33);
    }

    // ---- FILL_BLANKS_READING (shared word bank, correctGapIndex) ----

    // 3 gaps: gap0 correct=orderIndex0, gap1 correct=orderIndex4, gap2 correct=orderIndex6; 2 distractors.
    private static final String FILL_BLANKS_READING_OPTIONS =
            "[{\"text\":\"tragic\",\"correct\":true,\"orderIndex\":0,\"correctGapIndex\":0},"
                    + "{\"text\":\"boring\",\"correct\":false,\"orderIndex\":1},"
                    + "{\"text\":\"nastiness\",\"correct\":true,\"orderIndex\":4,\"correctGapIndex\":1},"
                    + "{\"text\":\"kindness\",\"correct\":false,\"orderIndex\":5},"
                    + "{\"text\":\"twists\",\"correct\":true,\"orderIndex\":6,\"correctGapIndex\":2}]";

    @Test
    void fillBlanksReading_allGapsCorrect_scoresFullMarks() {
        int score = service.score(answer(ScoringConstants.TASK_TYPE_FILL_BLANKS_READING, FILL_BLANKS_READING_OPTIONS, "0,4,6"));
        assertThat(score).isEqualTo(100);
    }

    @Test
    void fillBlanksReading_leadingEmptyEntry_gap0UnansweredScoresPartial() {
        int score = service.score(answer(ScoringConstants.TASK_TYPE_FILL_BLANKS_READING, FILL_BLANKS_READING_OPTIONS, ",4,6"));
        assertThat(score).isEqualTo(67); // round(2/3 * 100)
    }

    @Test
    void fillBlanksReading_middleEmptyEntry_gap1UnansweredScoresPartial() {
        int score = service.score(answer(ScoringConstants.TASK_TYPE_FILL_BLANKS_READING, FILL_BLANKS_READING_OPTIONS, "0,,6"));
        assertThat(score).isEqualTo(67);
    }

    @Test
    void fillBlanksReading_trailingEmptyEntry_gap2UnansweredParsesAsThreePositions_notTwo() {
        // The HIGH-risk case: "0,4," must parse as 3 positions (gap2 empty),
        // not 2 (which would misalign every gap index and silently corrupt
        // scoring for every task with an unanswered final gap).
        int score = service.score(answer(ScoringConstants.TASK_TYPE_FILL_BLANKS_READING, FILL_BLANKS_READING_OPTIONS, "0,4,"));
        assertThat(score).isEqualTo(67);
    }

    @Test
    void fillBlanksReading_wrongWordInGap_scoresIncorrectForThatGapOnly() {
        // gap0 answered with the gap1-correct word instead of its own.
        int score = service.score(answer(ScoringConstants.TASK_TYPE_FILL_BLANKS_READING, FILL_BLANKS_READING_OPTIONS, "4,4,6"));
        assertThat(score).isEqualTo(67);
    }

    // ---- FILL_BLANKS_READING_WRITING (per-blank groups, blankIndex) ----

    private static final String FILL_BLANKS_WRITING_OPTIONS =
            "[{\"text\":\"efficiently\",\"correct\":true,\"orderIndex\":0,\"blankIndex\":0},"
                    + "{\"text\":\"quickly\",\"correct\":false,\"orderIndex\":1,\"blankIndex\":0},"
                    + "{\"text\":\"using\",\"correct\":true,\"orderIndex\":0,\"blankIndex\":1},"
                    + "{\"text\":\"needing\",\"correct\":false,\"orderIndex\":1,\"blankIndex\":1}]";

    @Test
    void fillBlanksReadingWriting_bothGapsCorrect_scoresFullMarks() {
        int score = service.score(
                answer(ScoringConstants.TASK_TYPE_FILL_BLANKS_READING_WRITING, FILL_BLANKS_WRITING_OPTIONS, "0,0"));
        assertThat(score).isEqualTo(100);
    }

    @Test
    void fillBlanksReadingWriting_trailingEmptyEntry_secondGapUnanswered() {
        int score = service.score(
                answer(ScoringConstants.TASK_TYPE_FILL_BLANKS_READING_WRITING, FILL_BLANKS_WRITING_OPTIONS, "0,"));
        assertThat(score).isEqualTo(50);
    }

    @Test
    void fillBlanksReadingWriting_wrongOptionInEachGap_scoresZero() {
        int score = service.score(
                answer(ScoringConstants.TASK_TYPE_FILL_BLANKS_READING_WRITING, FILL_BLANKS_WRITING_OPTIONS, "1,1"));
        assertThat(score).isZero();
    }
}
