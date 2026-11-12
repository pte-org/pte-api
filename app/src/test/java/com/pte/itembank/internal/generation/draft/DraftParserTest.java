package com.pte.itembank.internal.generation.draft;

import tools.jackson.databind.json.JsonMapper;
import com.pte.itembank.internal.generation.exception.GenerationRequestInvalidException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DraftParserTest {

    private final DraftParser parser = new DraftParser(new JsonMapper());

    @Test
    void parseChoiceDraft_Valid() {
        String json = """
                {
                  "topic": "Environment",
                  "passage": "Climate change is a pressing issue.",
                  "question": "What is the main concern?",
                  "options": ["Climate change", "Weather", "Seasons"],
                  "correctIndexes": [0]
                }
                """;

        ItemDraft draft = parser.parse(json, "MC_READING_SINGLE");
        assertInstanceOf(ItemDraft.ChoiceDraft.class, draft);

        var choice = (ItemDraft.ChoiceDraft) draft;
        assertEquals("Environment", choice.topic());
        assertEquals("Climate change is a pressing issue.", choice.passage());
        assertEquals(3, choice.options().size());
        assertTrue(choice.correctIndexes().contains(0));
    }

    @Test
    void parseChoiceDraft_MissingField() {
        String json = """
                {
                  "topic": "Environment",
                  "passage": "Climate change is a pressing issue.",
                  "question": "What is the main concern?"
                }
                """;

        assertThrows(GenerationRequestInvalidException.class, () ->
                parser.parse(json, "MC_READING_SINGLE"));
    }

    @Test
    void parseBlankFillDraft_Valid() {
        String json = """
                {
                  "topic": "History",
                  "passageWithSlots": "The __BLANK__ was founded in 1776.",
                  "slots": [
                    {
                      "answer": "USA",
                      "distractors": ["Britain", "France", "Spain"]
                    }
                  ]
                }
                """;

        ItemDraft draft = parser.parse(json, "FILL_IN_THE_BLANKS_DROPDOWN");
        assertInstanceOf(ItemDraft.BlankFillDraft.class, draft);

        var blank = (ItemDraft.BlankFillDraft) draft;
        assertEquals(1, blank.slots().size());
        assertEquals("USA", blank.slots().get(0).answer());
        assertEquals(3, blank.slots().get(0).distractors().size());
    }

    @Test
    void parseReorderDraft_Valid() {
        String json = """
                {
                  "topic": "Process",
                  "paragraphs": [
                    "First step",
                    "Second step",
                    "Third step",
                    "Fourth step"
                  ]
                }
                """;

        ItemDraft draft = parser.parse(json, "RE_ORDER_PARAGRAPHS");
        assertInstanceOf(ItemDraft.ReorderDraft.class, draft);

        var reorder = (ItemDraft.ReorderDraft) draft;
        assertEquals(4, reorder.paragraphs().size());
        assertEquals("First step", reorder.paragraphs().get(0));
    }

    @Test
    void parsePassageDraft_WithKeyPoints() {
        String json = """
                {
                  "topic": "Energy",
                  "passage": "Solar power is growing.",
                  "keyPoints": ["Clean energy", "Renewable", "Cost-effective"],
                  "essayPrompt": null
                }
                """;

        ItemDraft draft = parser.parse(json, "SUMMARIZE_WRITTEN_TEXT");
        assertInstanceOf(ItemDraft.PassageDraft.class, draft);

        var passage = (ItemDraft.PassageDraft) draft;
        assertEquals("Solar power is growing.", passage.passage());
        assertEquals(3, passage.keyPoints().size());
        assertNull(passage.essayPrompt());
    }

    @Test
    void parsePassageDraft_WithEssayPrompt() {
        String json = """
                {
                  "topic": "Technology",
                  "passage": "",
                  "keyPoints": null,
                  "essayPrompt": "Discuss the impact of AI on society."
                }
                """;

        ItemDraft draft = parser.parse(json, "WRITE_ESSAY");
        assertInstanceOf(ItemDraft.PassageDraft.class, draft);

        var passage = (ItemDraft.PassageDraft) draft;
        assertEquals("Discuss the impact of AI on society.", passage.essayPrompt());
        assertNull(passage.keyPoints());
    }

    @Test
    void parseChoiceDraft_EmptyOptions() {
        String json = """
                {
                  "topic": "Test",
                  "passage": "Test",
                  "question": "Test",
                  "options": [],
                  "correctIndexes": []
                }
                """;

        assertThrows(GenerationRequestInvalidException.class, () ->
                parser.parse(json, "MC_READING_SINGLE"));
    }

    @Test
    void parseBlankFillDraft_EmptyAnswer() {
        String json = """
                {
                  "topic": "Test",
                  "passageWithSlots": "Test",
                  "slots": [
                    {
                      "answer": "",
                      "distractors": ["a", "b", "c"]
                    }
                  ]
                }
                """;

        assertThrows(GenerationRequestInvalidException.class, () ->
                parser.parse(json, "FILL_IN_THE_BLANKS_DROPDOWN"));
    }

    @Test
    void parseHint_ReadsBothLanguagesAtTheRoot() {
        AnswerHint hint = parser.parseHint("{\"answerHintVi\": \"Nghe ý chính\", \"answerHintEn\": \"Listen for the gist\"}");
        assertEquals("Nghe ý chính", hint.vi());
        assertEquals("Listen for the gist", hint.en());
    }

    @Test
    void parseHint_MissingLanguageIsASchemaError() {
        assertThrows(GenerationRequestInvalidException.class, () ->
                parser.parseHint("{\"answerHintVi\": \"Nghe ý chính\"}"));
    }

    @Test
    void parseHiwDraft_WorksOutTheChangedPositionsAndIgnoresTheModelsCount() {
        String json = """
                {
                  "topic": "Health",
                  "transcript": "Regular exercise brings many benefits for the body and mind.",
                  "displayedText": "Regular exercise brings several benefits for the brain and mind.",
                  "changedIndexes": [1, 2]
                }
                """;

        var hiw = (ItemDraft.HiwDraft) parser.parse(json, "HIGHLIGHT_INCORRECT_WORDS");

        assertArrayEquals(new int[] {3, 7}, hiw.changedIndexes());
    }

    @Test
    void changedIndexes_DifferentWordCountsHaveNoPositions() {
        assertArrayEquals(new int[0], HiwWordDiff.changedIndexes("one two three", "one two"));
    }
}
