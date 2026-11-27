package com.pte.practice.internal.service.response;

import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.dto.response.PracticeItemResultResponse.OptionVerdict;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Which of a student's picks landed, for the answers scored against a pinned key. */
class PracticeOptionVerdictReaderTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String CHOICE_KEY = """
            [{"text":"a","correct":true,"orderIndex":0},{"text":"b","correct":false,"orderIndex":1},
             {"text":"c","correct":true,"orderIndex":2},{"text":"d","correct":false,"orderIndex":3}]""";

    /** Two gaps, two words each; "popular" and "easy" are the right ones. */
    private static final String BLANK_KEY = """
            [{"text":"popular","correct":true,"orderIndex":0,"blankIndex":0},
             {"text":"empty","correct":false,"orderIndex":1,"blankIndex":0},
             {"text":"easy","correct":true,"orderIndex":2,"blankIndex":1},
             {"text":"hard","correct":false,"orderIndex":3,"blankIndex":1}]""";

    private static List<OptionVerdict> read(String rendererKey, String payload, String key) {
        PracticeResponse item = new PracticeResponse();
        item.setRendererKey(rendererKey);
        item.setSavedPayload(payload);
        item.setObjectiveOptionsJson(key);
        return PracticeOptionVerdictReader.read(JSON, item);
    }

    @Test
    void marksEachPickedOptionOfAMultipleChoiceAnswerRightOrWrong() {
        assertThat(read("MC_READING_MULTIPLE_V1", "{\"selectedOptions\":[\"0\",\"1\"]}", CHOICE_KEY)).containsExactly(
                new OptionVerdict(0, true, true, "a"),
                new OptionVerdict(1, true, false, "b"),
                new OptionVerdict(2, false, true, "c"),
                new OptionVerdict(3, false, false, "d"));
    }

    @Test
    void marksThePickedOptionOfASingleChoiceAnswerAndSortsByPosition() {
        assertThat(read("HIGHLIGHT_CORRECT_SUMMARY_V1", "{\"selectedOption\":\"2\"}",
                "[{\"orderIndex\":1,\"correct\":false,\"text\":\"b\"},{\"orderIndex\":2,\"correct\":false,\"text\":\"c\"},"
                        + "{\"orderIndex\":0,\"correct\":true,\"text\":\"a\"}]"))
                .extracting(OptionVerdict::orderIndex).containsExactly(0, 1, 2);
    }

    @Test
    void marksTheWordPutInEachGapOfADropdownAnswerAgainstThatGapsKey() {
        assertThat(read("FILL_IN_THE_BLANKS_DROPDOWN_V1", "{\"selections\":{\"0\":\"popular\",\"1\":\"hard\"}}",
                BLANK_KEY)).containsExactly(
                new OptionVerdict(0, true, true, 0, "popular"),
                new OptionVerdict(1, false, false, 0, "empty"),
                new OptionVerdict(2, false, true, 1, "easy"),
                new OptionVerdict(3, true, false, 1, "hard"));
    }

    @Test
    void readsADragAndDropAnswerFromItsOwnFieldAndLeavesAnEmptyGapUnpicked() {
        assertThat(read("FILL_IN_THE_BLANKS_DRAG_AND_DROP_V1", "{\"placements\":{\"0\":\"popular\",\"1\":\"\"}}",
                BLANK_KEY)).filteredOn(OptionVerdict::selected)
                .containsExactly(new OptionVerdict(0, true, true, 0, "popular"));
    }

    @Test
    void doesNotLetTheSameWordInAnotherGapCountAsAPick() {
        // "easy" belongs to gap 1; putting it in gap 0 must not mark gap 1 answered.
        assertThat(read("FILL_IN_THE_BLANKS_DROPDOWN_V1", "{\"selections\":{\"0\":\"easy\"}}", BLANK_KEY))
                .filteredOn(OptionVerdict::selected).isEmpty();
    }

    @Test
    void anAnswerThatHasNoOptionKeyOrCannotBeReadHasNoVerdicts() {
        assertThat(read("READ_ALOUD_V1", "{\"selectedOption\":\"0\"}", CHOICE_KEY)).isEmpty();
        assertThat(read("RE_ORDER_PARAGRAPHS_V1", "{\"order\":[\"0\"]}", CHOICE_KEY)).isEmpty();
        assertThat(read("MC_READING_MULTIPLE_V1", "not json", CHOICE_KEY)).isEmpty();
        assertThat(read("MC_READING_MULTIPLE_V1", "{\"selectedOptions\":[\"0\"]}", "{}")).isEmpty();
        assertThat(read("MC_READING_MULTIPLE_V1", null, CHOICE_KEY)).isEmpty();
        assertThat(read(null, "{\"selectedOptions\":[\"0\"]}", CHOICE_KEY)).isEmpty();
    }

    @Test
    void aKeyWithNoCorrectOptionGivesNoVerdictsSoNothingIsMarkedWrongByMistake() {
        assertThat(read("MC_READING_SINGLE_V1", "{\"selectedOption\":\"0\"}",
                "[{\"orderIndex\":0,\"correct\":false,\"text\":\"a\"}]")).isEmpty();
    }

    @Test
    void anIdThatIsNotAnOptionPositionIsIgnoredAndTheRestStillCounts() {
        assertThat(read("MC_LISTENING_MULTIPLE_V1", "{\"selectedOptions\":[\"x\",\"2\"]}", CHOICE_KEY))
                .filteredOn(OptionVerdict::selected).containsExactly(new OptionVerdict(2, true, true, "c"));
    }
}
