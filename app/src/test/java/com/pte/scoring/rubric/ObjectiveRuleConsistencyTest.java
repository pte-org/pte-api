package com.pte.scoring.rubric;

import com.pte.scoring.internal.service.objective.ObjectiveScoringService;
import com.pte.scoring.support.SubmittedAnswerFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import static com.pte.scoring.rubric.RubricTestSupport.task;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The declared objective formula for each task type must match what {@link ObjectiveScoringService} really does,
 * so the rubric page never describes a rule the scorer does not apply. One behavioural case per rule code.
 */
class ObjectiveRuleConsistencyTest {

    private final ObjectiveScoringService service = new ObjectiveScoringService(JsonMapper.builder().build());

    private static final String SINGLE = "[{\"text\":\"a\",\"correct\":false,\"orderIndex\":0},"
            + "{\"text\":\"b\",\"correct\":true,\"orderIndex\":1}]";
    private static final String MULTIPLE = "[{\"text\":\"a\",\"correct\":true,\"orderIndex\":0},"
            + "{\"text\":\"b\",\"correct\":true,\"orderIndex\":1},"
            + "{\"text\":\"c\",\"correct\":false,\"orderIndex\":2},"
            + "{\"text\":\"d\",\"correct\":false,\"orderIndex\":3}]";
    private static final String REORDER = "[{\"text\":\"p0\",\"correct\":false,\"orderIndex\":0},"
            + "{\"text\":\"p1\",\"correct\":false,\"orderIndex\":1},"
            + "{\"text\":\"p2\",\"correct\":false,\"orderIndex\":2},"
            + "{\"text\":\"p3\",\"correct\":false,\"orderIndex\":3}]";
    private static final String DRAG_AND_DROP = "[{\"text\":\"x\",\"correct\":true,\"orderIndex\":0,\"correctGapIndex\":0},"
            + "{\"text\":\"y\",\"correct\":false,\"orderIndex\":1},"
            + "{\"text\":\"z\",\"correct\":true,\"orderIndex\":4,\"correctGapIndex\":1},"
            + "{\"text\":\"w\",\"correct\":true,\"orderIndex\":6,\"correctGapIndex\":2}]";
    private static final String DROPDOWN = "[{\"text\":\"e\",\"correct\":true,\"orderIndex\":0,\"blankIndex\":0},"
            + "{\"text\":\"q\",\"correct\":false,\"orderIndex\":1,\"blankIndex\":0},"
            + "{\"text\":\"u\",\"correct\":true,\"orderIndex\":0,\"blankIndex\":1},"
            + "{\"text\":\"n\",\"correct\":false,\"orderIndex\":1,\"blankIndex\":1}]";

    private int score(String type, String options, String payload, String reference) {
        return service.score(SubmittedAnswerFixtures.answer(type, options, payload, reference));
    }

    private static String ruleCode(String type) {
        return task(type).objectiveRule().code();
    }

    @ParameterizedTest
    @CsvSource({"MC_READING_SINGLE", "MC_LISTENING_SINGLE", "HIGHLIGHT_CORRECT_SUMMARY", "SELECT_MISSING_WORD"})
    void correctIncorrect_allOrNothing(String type) {
        assertThat(ruleCode(type)).isEqualTo("CORRECT_INCORRECT");

        assertThat(score(type, SINGLE, "1", null)).isEqualTo(100);
        assertThat(score(type, SINGLE, "0", null)).isZero();
    }

    @ParameterizedTest
    @CsvSource({"MC_READING_MULTIPLE", "MC_LISTENING_MULTIPLE"})
    void plusMinusFloorZero_multipleChoice(String type) {
        assertThat(ruleCode(type)).isEqualTo("PLUS_MINUS_FLOOR_ZERO");

        // 1 correct + 2 wrong selections: 1 - 2 is floored at 0, never negative.
        assertThat(score(type, MULTIPLE, "0,2,3", null)).isZero();
        assertThat(score(type, MULTIPLE, "0,1", null)).isEqualTo(100);
        assertThat(score(type, MULTIPLE, "0,1,2", null)).isEqualTo(50); // 2 - 1 = 1 of 2
    }

    @Test
    void plusMinusFloorZero_highlightIncorrectWords() {
        assertThat(ruleCode("HIGHLIGHT_INCORRECT_WORDS")).isEqualTo("PLUS_MINUS_FLOOR_ZERO");

        assertThat(score("HIGHLIGHT_INCORRECT_WORDS", null, "1,0,2", "[1,3]")).isZero();
        assertThat(score("HIGHLIGHT_INCORRECT_WORDS", null, "1,3", "[1,3]")).isEqualTo(100);
        assertThat(score("HIGHLIGHT_INCORRECT_WORDS", null, "1,3,5", "[1,3]")).isEqualTo(50);
    }

    @Test
    void adjacentPairs_reOrderParagraphs() {
        assertThat(ruleCode("RE_ORDER_PARAGRAPHS")).isEqualTo("ADJACENT_PAIRS");

        assertThat(score("RE_ORDER_PARAGRAPHS", REORDER, "0,1,2,3", null)).isEqualTo(100);
        assertThat(score("RE_ORDER_PARAGRAPHS", REORDER, "0,1,3,2", null)).isEqualTo(33); // 1 of 3 pairs
        assertThat(score("RE_ORDER_PARAGRAPHS", REORDER, "3,2,1,0", null)).isZero();
    }

    @Test
    void perBlank_dragAndDrop() {
        assertThat(ruleCode("FILL_IN_THE_BLANKS_DRAG_AND_DROP")).isEqualTo("PER_BLANK");

        assertThat(score("FILL_IN_THE_BLANKS_DRAG_AND_DROP", DRAG_AND_DROP, "0,4,6", null)).isEqualTo(100);
        assertThat(score("FILL_IN_THE_BLANKS_DRAG_AND_DROP", DRAG_AND_DROP, "0,4,1", null)).isEqualTo(67); // 2 of 3
    }

    @Test
    void perBlank_dropdown() {
        assertThat(ruleCode("FILL_IN_THE_BLANKS_DROPDOWN")).isEqualTo("PER_BLANK");

        assertThat(score("FILL_IN_THE_BLANKS_DROPDOWN", DROPDOWN, "0,0", null)).isEqualTo(100);
        assertThat(score("FILL_IN_THE_BLANKS_DROPDOWN", DROPDOWN, "0,1", null)).isEqualTo(50);
    }

    @Test
    void perBlank_typeIn() {
        assertThat(ruleCode("FILL_IN_THE_BLANKS_TYPE_IN")).isEqualTo("PER_BLANK");

        assertThat(score("FILL_IN_THE_BLANKS_TYPE_IN", null, "cat,dog", "[\"cat\",\"dog\"]")).isEqualTo(100);
        assertThat(score("FILL_IN_THE_BLANKS_TYPE_IN", null, "cat,bird", "[\"cat\",\"dog\"]")).isEqualTo(50);
    }

    @Test
    void lcsWords_writeFromDictation() {
        assertThat(ruleCode("WRITE_FROM_DICTATION")).isEqualTo("LCS_WORDS");

        String reference = "The quick brown fox";
        assertThat(score("WRITE_FROM_DICTATION", null, "the quick brown fox", reference)).isEqualTo(100);
        // Longest common subsequence of the words is 3 of 4: order matters, a swapped pair costs one word.
        assertThat(score("WRITE_FROM_DICTATION", null, "the brown quick fox", reference)).isEqualTo(75);
    }
}
