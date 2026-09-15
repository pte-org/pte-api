package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.internal.constant.ScoringConstants;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers objective evaluators for Reading and the option-based Listening
 * types: multiple-choice negative marking, Re-order adjacent-pair partial
 * credit, and positional fill-blanks scoring (including the HIGH-risk
 * trailing-empty-entry case).
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
    void supports_allTwelveObjectiveTaskTypes() {
        assertThat(service.supports(ScoringConstants.TASK_TYPE_MC_READING_SINGLE)).isTrue();
        assertThat(service.supports(ScoringConstants.TASK_TYPE_MC_READING_MULTIPLE)).isTrue();
        assertThat(service.supports(ScoringConstants.TASK_TYPE_RE_ORDER_PARAGRAPHS)).isTrue();
        assertThat(service.supports(ScoringConstants.TASK_TYPE_FILL_BLANKS_READING)).isTrue();
        assertThat(service.supports(ScoringConstants.TASK_TYPE_FILL_BLANKS_READING_WRITING)).isTrue();
        assertThat(service.supports(ScoringConstants.TASK_TYPE_MC_LISTENING_SINGLE)).isTrue();
        assertThat(service.supports(ScoringConstants.TASK_TYPE_MC_LISTENING_MULTIPLE)).isTrue();
        assertThat(service.supports(ScoringConstants.TASK_TYPE_HIGHLIGHT_CORRECT_SUMMARY)).isTrue();
        assertThat(service.supports(ScoringConstants.TASK_TYPE_SELECT_MISSING_WORD)).isTrue();
        assertThat(service.supports(ScoringConstants.TASK_TYPE_FILL_BLANKS_LISTENING)).isTrue();
        assertThat(service.supports(ScoringConstants.TASK_TYPE_HIGHLIGHT_INCORRECT_WORDS)).isTrue();
        assertThat(service.supports(ScoringConstants.TASK_TYPE_WRITE_FROM_DICTATION)).isTrue();
        assertThat(service.supports(null)).isFalse();
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

    // ---- Option-based Listening single-answer types ----

    @Test
    void mcListeningSingle_correctSelection_scoresFullMarks() {
        int score = service.score(answer(ScoringConstants.TASK_TYPE_MC_LISTENING_SINGLE, MC_SINGLE_OPTIONS, "1"));
        assertThat(score).isEqualTo(100);
    }

    @Test
    void highlightCorrectSummary_incorrectSelection_scoresZero() {
        int score = service.score(
                answer(ScoringConstants.TASK_TYPE_HIGHLIGHT_CORRECT_SUMMARY, MC_SINGLE_OPTIONS, "0"));
        assertThat(score).isZero();
    }

    @Test
    void highlightCorrectSummary_correctSelection_scoresFullMarks() {
        int score = service.score(
                answer(ScoringConstants.TASK_TYPE_HIGHLIGHT_CORRECT_SUMMARY, MC_SINGLE_OPTIONS, "1"));
        assertThat(score).isEqualTo(100);
    }

    @Test
    void selectMissingWord_emptySelection_scoresZero() {
        int score = service.score(answer(ScoringConstants.TASK_TYPE_SELECT_MISSING_WORD, MC_SINGLE_OPTIONS, ""));
        assertThat(score).isZero();
    }

    @Test
    void selectMissingWord_correctSelection_scoresFullMarks() {
        int score = service.score(answer(ScoringConstants.TASK_TYPE_SELECT_MISSING_WORD, MC_SINGLE_OPTIONS, "1"));
        assertThat(score).isEqualTo(100);
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

    @Test
    void mcListeningMultiple_allCorrectSelected_scoresFullMarks() {
        int score = service.score(
                answer(ScoringConstants.TASK_TYPE_MC_LISTENING_MULTIPLE, MC_MULTIPLE_OPTIONS, "1,0"));
        assertThat(score).isEqualTo(100);
    }

    @Test
    void mcListeningMultiple_oneCorrectSelection_scoresPartialCredit() {
        int score = service.score(
                answer(ScoringConstants.TASK_TYPE_MC_LISTENING_MULTIPLE, MC_MULTIPLE_OPTIONS, "0"));
        assertThat(score).isEqualTo(50);
    }

    @Test
    void mcListeningMultiple_incorrectSelection_isFlooredAtZero() {
        int score = service.score(
                answer(ScoringConstants.TASK_TYPE_MC_LISTENING_MULTIPLE, MC_MULTIPLE_OPTIONS, "2"));
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

    @Test
    void reOrderParagraphs_malformedOptions_neverScoresFullMarks() {
        int score = service.score(
                answer(ScoringConstants.TASK_TYPE_RE_ORDER_PARAGRAPHS, "not-json", "0"));
        assertThat(score).isZero();
    }

    @Test
    void reOrderParagraphs_singleOption_requiresAValidSubmittedPosition() {
        String oneOption = "[{\"text\":\"p0\",\"correct\":false,\"orderIndex\":0}]";

        assertThat(service.score(
                answer(ScoringConstants.TASK_TYPE_RE_ORDER_PARAGRAPHS, oneOption, "0"))).isEqualTo(100);
        assertThat(service.score(
                answer(ScoringConstants.TASK_TYPE_RE_ORDER_PARAGRAPHS, oneOption, ""))).isZero();
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

    // ---- Listening reference-based types ----

    private ScoringAnswer referenceAnswer(String taskType, String correctAnswerText, String payload) {
        ScoringAnswer answer = answer(taskType, null, payload);
        answer.setCorrectAnswerText(correctAnswerText);
        return answer;
    }

    @Test
    void fillBlanksListening_allGapsCorrect_ignoresCaseAndOuterWhitespace() {
        int score = service.score(referenceAnswer(
                ScoringConstants.TASK_TYPE_FILL_BLANKS_LISTENING,
                "[\"rapid\",\"forest\"]",
                " RAPID , forest "));
        assertThat(score).isEqualTo(100);
    }

    @Test
    void fillBlanksListening_trailingEmptyEntry_scoresOnlyAnsweredGaps() {
        int score = service.score(referenceAnswer(
                ScoringConstants.TASK_TYPE_FILL_BLANKS_LISTENING,
                "[\"rapid\",\"forest\"]",
                "rapid,"));
        assertThat(score).isEqualTo(50);
    }

    @Test
    void fillBlanksListening_malformedReference_failsClosedAtZero() {
        int score = service.score(referenceAnswer(
                ScoringConstants.TASK_TYPE_FILL_BLANKS_LISTENING,
                "not-json",
                "rapid,forest"));
        assertThat(score).isZero();
    }

    @Test
    void highlightIncorrectWords_allExpectedPositionsSelected_scoresFullMarks() {
        int score = service.score(referenceAnswer(
                ScoringConstants.TASK_TYPE_HIGHLIGHT_INCORRECT_WORDS,
                "[3,7,11]",
                "11,3,7"));
        assertThat(score).isEqualTo(100);
    }

    @Test
    void highlightIncorrectWords_unexpectedSelection_appliesNegativeMarking() {
        int score = service.score(referenceAnswer(
                ScoringConstants.TASK_TYPE_HIGHLIGHT_INCORRECT_WORDS,
                "[3,7,11]",
                "3,7,99"));
        assertThat(score).isEqualTo(33);
    }

    @Test
    void highlightIncorrectWords_malformedReference_failsClosedAtZero() {
        int score = service.score(referenceAnswer(
                ScoringConstants.TASK_TYPE_HIGHLIGHT_INCORRECT_WORDS,
                "[3,3]",
                "3"));
        assertThat(score).isZero();
    }

    @Test
    void writeFromDictation_exactWordsWithDifferentCaseAndPunctuation_scoresFullMarks() {
        int score = service.score(referenceAnswer(
                ScoringConstants.TASK_TYPE_WRITE_FROM_DICTATION,
                "The results are available online.",
                "the results are available online"));
        assertThat(score).isEqualTo(100);
    }

    @Test
    void writeFromDictation_missingWords_scoresOrderedPartialCredit() {
        int score = service.score(referenceAnswer(
                ScoringConstants.TASK_TYPE_WRITE_FROM_DICTATION,
                "The results are available online",
                "The results are online"));
        assertThat(score).isEqualTo(80);
    }

    @Test
    void writeFromDictation_repeatedWords_preservesOrderedMatching() {
        int score = service.score(referenceAnswer(
                ScoringConstants.TASK_TYPE_WRITE_FROM_DICTATION,
                "the data that the team collected",
                "the that the team collected"));
        assertThat(score).isEqualTo(83);
    }

    @Test
    void writeFromDictation_extraWord_doesNotReduceMatchedReferenceWords() {
        int score = service.score(referenceAnswer(
                ScoringConstants.TASK_TYPE_WRITE_FROM_DICTATION,
                "the results are available online",
                "the results are now available online"));
        assertThat(score).isEqualTo(100);
    }

    @Test
    void writeFromDictation_emptyReference_failsClosedAtZero() {
        int score = service.score(referenceAnswer(
                ScoringConstants.TASK_TYPE_WRITE_FROM_DICTATION,
                " ",
                "any answer"));
        assertThat(score).isZero();
    }
}
