package com.pte.itembank.internal.generation.draft;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;
import com.pte.itembank.dto.request.CreateQuestionRequest;
import com.pte.itembank.internal.generation.draft.audio.AudioDraftMapper;
import com.pte.itembank.internal.generation.draft.audio.DialogueDraftMapper;
import com.pte.itembank.internal.generation.draft.audio.HiwDraftMapper;
import com.pte.itembank.internal.generation.draft.image.ChartDraftMapper;
import com.pte.itembank.internal.generation.draft.text.TextDraftMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DraftToQuestionMapperTest {

    private final DraftToQuestionMapper mapper = new DraftToQuestionMapper(
            new TextDraftMapper(),
            new AudioDraftMapper(),
            new HiwDraftMapper(),
            new DialogueDraftMapper(),
            new ChartDraftMapper());

    @Test
    void mapChoiceSingle_CreatesCorrectRequest() {
        var draft = new ItemDraft.ChoiceDraft(
                "Environment",
                "Climate change affects global systems.",
                "What is mentioned as affected?",
                List.of("Global systems", "Local weather", "Personal choices", "Technology"),
                List.of(0));

        var request = mapper.map(draft, CefrLevel.B2, QuestionPool.EXAM, "MC_READING_SINGLE");

        assertEquals("MC_READING_SINGLE", request.pteTaskType());
        assertEquals("MC_READING_SINGLE", request.taskTypeKey());
        assertNotNull(request.promptText());
        assertTrue(request.promptText().contains("Climate change affects global systems."));
        assertTrue(request.promptText().contains("What is mentioned as affected?"));
        assertEquals(4, request.options().size());
        assertTrue(request.options().get(0).correct());
        assertFalse(request.options().get(1).correct());
        assertEquals(QuestionPool.EXAM.name(), request.pool());
        assertEquals(CefrLevel.B2.name(), request.cefrLevel());
    }

    @Test
    void mapChoiceMultiple_CreatesCorrectRequest() {
        var draft = new ItemDraft.ChoiceDraft(
                "Topic",
                "Passage here",
                "Which are true?",
                List.of("A", "B", "C", "D", "E"),
                List.of(0, 2));

        var request = mapper.map(draft, CefrLevel.C1, QuestionPool.PRACTICE, "MC_READING_MULTIPLE");

        assertEquals("MC_READING_MULTIPLE", request.pteTaskType());
        assertEquals(5, request.options().size());
        assertTrue(request.options().get(0).correct());
        assertFalse(request.options().get(1).correct());
        assertTrue(request.options().get(2).correct());
        assertEquals(QuestionPool.PRACTICE.name(), request.pool());
    }

    @Test
    void mapBlankFillDropdown_CreatesCorrectRequest() {
        var slots = List.of(
                new ItemDraft.BlankFillDraft.Slot("answer1", List.of("dist1", "dist2", "dist3")),
                new ItemDraft.BlankFillDraft.Slot("answer2", List.of("dist4", "dist5", "dist6")));

        var draft = new ItemDraft.BlankFillDraft("Topic", "Text with __BLANK__ and __BLANK__", slots);

        var request = mapper.map(draft, CefrLevel.B1, QuestionPool.EXAM, "FILL_IN_THE_BLANKS_DROPDOWN");

        assertEquals("FILL_IN_THE_BLANKS_DROPDOWN", request.pteTaskType());
        assertEquals(8, request.options().size()); // 2 slots * 4 options each

        // First blank options
        assertTrue(request.options().get(0).correct());
        assertEquals(0, request.options().get(0).blankIndex());
        assertFalse(request.options().get(1).correct());

        // Second blank options start at index 4
        assertTrue(request.options().get(4).correct());
        assertEquals(1, request.options().get(4).blankIndex());
    }

    @Test
    void mapBlankFillDragDrop_CreatesCorrectRequest() {
        var slots = List.of(
                new ItemDraft.BlankFillDraft.Slot("word1", List.of("extra1", "extra2")),
                new ItemDraft.BlankFillDraft.Slot("word2", List.of("extra3", "extra4")));

        var draft = new ItemDraft.BlankFillDraft("Topic", "Text with blanks", slots);

        var request = mapper.map(draft, CefrLevel.B2, QuestionPool.PRACTICE, "FILL_IN_THE_BLANKS_DRAG_AND_DROP");

        assertEquals("FILL_IN_THE_BLANKS_DRAG_AND_DROP", request.pteTaskType());
        // 2 correct answers + 4 extra words = 6 options
        assertEquals(6, request.options().size());

        // Verify first answer has correctGapIndex set
        assertTrue(request.options().get(0).correct());
        assertEquals(0, request.options().get(0).correctGapIndex());
        // The bank rejects an option that carries both indexes; drag-drop words use correctGapIndex only.
        assertTrue(request.options().stream().noneMatch(o -> o.blankIndex() != null && o.correctGapIndex() != null));
        assertNull(request.options().get(0).blankIndex());
    }

    @Test
    void mapReorder_CreatesCorrectRequest() {
        var paragraphs = List.of("First", "Second", "Third", "Fourth");
        var draft = new ItemDraft.ReorderDraft("Topic", paragraphs);

        var request = mapper.map(draft, CefrLevel.B1, QuestionPool.EXAM, "RE_ORDER_PARAGRAPHS");

        assertEquals("RE_ORDER_PARAGRAPHS", request.pteTaskType());
        assertEquals(4, request.options().size());
        // All should be marked correct (they're in the right order)
        for (var opt : request.options()) {
            assertTrue(opt.correct());
        }
    }

    @Test
    void mapSummary_CreatesCorrectRequest() {
        String passage = "Solar energy is increasingly important for sustainable development. " +
                "Countries are investing in solar infrastructure. Technology continues to improve efficiency.";
        var draft = new ItemDraft.PassageDraft("Energy", passage,
                List.of("Solar energy importance", "Country investment", "Technology improvements"), null);

        var request = mapper.map(draft, CefrLevel.C1, QuestionPool.PRACTICE, "SUMMARIZE_WRITTEN_TEXT");

        assertEquals("SUMMARIZE_WRITTEN_TEXT", request.pteTaskType());
        assertEquals(passage, request.promptText());
        assertNotNull(request.referenceAnswerText());
        assertTrue(request.referenceAnswerText().contains("Solar energy importance"));
        assertEquals(30, request.minWordCount());
        assertEquals(75, request.maxWordCount());
    }

    @Test
    void mapEssay_CreatesCorrectRequest() {
        String prompt = "Some believe technology has improved our lives, while others argue it creates problems. Discuss both views.";
        var draft = new ItemDraft.PassageDraft("Technology", "", null, prompt);

        var request = mapper.map(draft, CefrLevel.B2, QuestionPool.EXAM, "WRITE_ESSAY");

        assertEquals("WRITE_ESSAY", request.pteTaskType());
        assertEquals(prompt, request.promptText());
        assertEquals(200, request.minWordCount());
        assertEquals(300, request.maxWordCount());
        assertNull(request.referenceAnswerText());
    }

    @Test
    void mapReadAloud_CreatesCorrectRequest() {
        String passage = "Urban parks offer green spaces for community recreation. These areas support mental and physical health.";
        var draft = new ItemDraft.PassageDraft("Parks", passage, null, null);

        var request = mapper.map(draft, CefrLevel.A2, QuestionPool.PRACTICE, "READ_ALOUD");

        assertEquals("READ_ALOUD", request.pteTaskType());
        assertEquals(passage, request.promptText());
        assertNull(request.options());
        assertNull(request.minWordCount());
        assertNull(request.maxWordCount());
    }
}
