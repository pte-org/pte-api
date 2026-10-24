package com.pte.practice.internal.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PracticeAnswerCanonicalizerTest {

    private static final String DRAG_AND_DROP_OPTIONS = """
            [{"text":"cat","correct":true,"orderIndex":0,"correctGapIndex":0},
             {"text":"dog","correct":true,"orderIndex":1,"correctGapIndex":1},
             {"text":"fish","correct":false,"orderIndex":2,"correctGapIndex":null}]
            """;
    private static final String DROPDOWN_OPTIONS = """
            [{"text":"red","correct":true,"orderIndex":0,"blankIndex":0},
             {"text":"blue","correct":false,"orderIndex":1,"blankIndex":0},
             {"text":"big","correct":false,"orderIndex":0,"blankIndex":1},
             {"text":"small","correct":true,"orderIndex":1,"blankIndex":1}]
            """;

    private final PracticeAnswerCanonicalizer canonicalizer =
            new PracticeAnswerCanonicalizer(JsonMapper.builder().build());

    @Test
    void singleChoiceRenderersSubmitTheSelectedOptionIndex() {
        for (String renderer : new String[] {"MC_READING_SINGLE_V1", "MC_LISTENING_SINGLE_V1",
                "SELECT_MISSING_WORD_V1", "HIGHLIGHT_CORRECT_SUMMARY_V1"}) {
            assertThat(canonical(renderer, "{\"selectedOption\":\"2\"}", null)).contains("2");
        }
    }

    @Test
    void multipleChoiceRenderersJoinTheSelectedIndexes() {
        for (String renderer : new String[] {"MC_READING_MULTIPLE_V1", "MC_LISTENING_MULTIPLE_V1"}) {
            assertThat(canonical(renderer, "{\"selectedOptions\":[\"1\",\"3\"]}", null)).contains("1,3");
        }
    }

    @Test
    void reOrderKeepsTheStudentSequence() {
        assertThat(canonical("RE_ORDER_PARAGRAPHS_V1", "{\"order\":[\"2\",\"0\",\"1\"]}", null))
                .contains("2,0,1");
    }

    @Test
    void dragAndDropMapsPlacedWordsToTheirOptionIndexByGap() {
        assertThat(canonical("FILL_IN_THE_BLANKS_DRAG_AND_DROP_V1",
                "{\"placements\":{\"0\":\"Dog\",\"1\":\"cat\"}}", DRAG_AND_DROP_OPTIONS)).contains("1,0");
    }

    @Test
    void dragAndDropMatchesWordsAcrossCaseAndWhitespaceRuns() {
        String options = "[{\"text\":\"ice  cream\",\"correct\":true,\"orderIndex\":0,\"correctGapIndex\":0}]";

        assertThat(canonical("FILL_IN_THE_BLANKS_DRAG_AND_DROP_V1",
                "{\"placements\":{\"0\":\" Ice\\t cream \"}}", options)).contains("0");
    }

    @Test
    void dragAndDropKeepsAnUnansweredBlankPositional() {
        assertThat(canonical("FILL_IN_THE_BLANKS_DRAG_AND_DROP_V1",
                "{\"placements\":{\"1\":\"cat\"}}", DRAG_AND_DROP_OPTIONS)).contains(",0");
    }

    @Test
    void dropdownMapsSelectionsWithinTheirOwnBlankGroup() {
        assertThat(canonical("FILL_IN_THE_BLANKS_DROPDOWN_V1",
                "{\"selections\":{\"0\":\"red\",\"1\":\"small\"}}", DROPDOWN_OPTIONS)).contains("0,1");
    }

    @Test
    void ambiguousWordBankTextFailsClosed() {
        String duplicated = """
                [{"text":"cat","correct":true,"orderIndex":0,"correctGapIndex":0},
                 {"text":"cat","correct":false,"orderIndex":1,"correctGapIndex":null}]
                """;
        assertThat(canonical("FILL_IN_THE_BLANKS_DRAG_AND_DROP_V1",
                "{\"placements\":{\"0\":\"cat\"}}", duplicated)).isEmpty();
    }

    @Test
    void textRenderersSubmitTheRawText() {
        for (String renderer : new String[] {"SUMMARIZE_WRITTEN_TEXT_V1", "WRITE_ESSAY_V1",
                "SUMMARIZE_SPOKEN_TEXT_V1", "WRITE_FROM_DICTATION_V1"}) {
            assertThat(canonical(renderer, "{\"text\":\"Hello, world.\"}", null)).contains("Hello, world.");
        }
    }

    @Test
    void typeInBlanksJoinPositionsAndKeepEmptyOnes() {
        assertThat(canonical("FILL_IN_THE_BLANKS_TYPE_IN_V1", "{\"blanks\":[\"a\",\"\",\"c\"]}", null))
                .contains("a,,c");
    }

    @Test
    void typeInBlankContainingACommaFailsClosed() {
        assertThat(canonical("FILL_IN_THE_BLANKS_TYPE_IN_V1", "{\"blanks\":[\"a,b\"]}", null)).isEmpty();
    }

    @Test
    void highlightIncorrectWordsJoinsTheTokenPositions() {
        assertThat(canonical("HIGHLIGHT_INCORRECT_WORDS_V1", "{\"selectedWordIndexes\":[3,7]}", null))
                .contains("3,7");
    }

    @Test
    void everySpeechRendererSubmitsTheMediaId() {
        UUID mediaId = UUID.randomUUID();
        String payload = "{\"mediaPublicId\":\"" + mediaId + "\",\"durationSeconds\":30}";
        for (String renderer : new String[] {"READ_ALOUD_V1", "REPEAT_SENTENCE_V1", "DESCRIBE_IMAGE_V1",
                "RE_TELL_LECTURE_V1", "ANSWER_SHORT_QUESTION_V1", "RESPOND_TO_A_SITUATION_V1",
                "SUMMARIZE_GROUP_DISCUSSION_V1"}) {
            assertThat(canonical(renderer, payload, null)).contains(mediaId.toString());
        }
    }

    @Test
    void unknownRendererOrMalformedPayloadIsNotCanonicalisable() {
        assertThat(canonical("NOT_A_RENDERER_V1", "{\"text\":\"x\"}", null)).isEmpty();
        assertThat(canonical("WRITE_ESSAY_V1", "not json", null)).isEmpty();
        assertThat(canonical("WRITE_ESSAY_V1", null, null)).isEmpty();
        assertThat(canonical("MC_READING_SINGLE_V1", "{\"selectedOption\":\"\"}", null)).isEmpty();
    }

    private Optional<String> canonical(String rendererKey, String payload, String optionsJson) {
        return canonicalizer.canonicalize(rendererKey, payload, optionsJson);
    }
}
