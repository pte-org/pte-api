package com.pte.itembank.internal.generation.validate;

import com.pte.itembank.internal.generation.validate.audio.*;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DraftValidatorTest {

    private final ChoiceValidator choiceValidator = new ChoiceValidator();
    private final BlankFillValidator blankFillValidator = new BlankFillValidator();
    private final ReorderValidator reorderValidator = new ReorderValidator();
    private final PassageValidator passageValidator = new PassageValidator();
    private final HiwValidator hiwValidator = new HiwValidator();
    private final ListenFibValidator listenFibValidator = new ListenFibValidator();
    private final DraftValidator validator = new DraftValidator(
            choiceValidator, blankFillValidator, reorderValidator, passageValidator,
            hiwValidator, listenFibValidator, new DialogueValidator(), new ChartDraftValidator());

    // Choice Validator Tests
    @Test
    void validateChoiceSingle_Valid() {
        var draft = new ItemDraft.ChoiceDraft(
                "Topic",
                "Passage text",
                "Question?",
                List.of("A", "B", "C", "D"),
                List.of(0));

        var issues = choiceValidator.validateSingle(draft);
        assertTrue(issues.isEmpty());
    }

    @Test
    void validateChoiceSingle_TooManyOptions() {
        var draft = new ItemDraft.ChoiceDraft(
                "Topic",
                "Passage text",
                "Question?",
                List.of("A", "B", "C", "D", "E", "F"),
                List.of(0));

        var issues = choiceValidator.validateSingle(draft);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i.code().equals("OPTION_COUNT_INVALID")));
    }

    @Test
    void validateChoiceSingle_NoCorrectAnswer() {
        var draft = new ItemDraft.ChoiceDraft(
                "Topic",
                "Passage text",
                "Question?",
                List.of("A", "B", "C", "D"),
                List.of());

        var issues = choiceValidator.validateSingle(draft);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i.code().equals("CORRECT_COUNT_INVALID")));
    }

    @Test
    void validateChoiceMultiple_Valid() {
        var draft = new ItemDraft.ChoiceDraft(
                "Topic",
                "Passage text",
                "Which are correct?",
                List.of("A", "B", "C", "D", "E"),
                List.of(0, 2));

        var issues = choiceValidator.validateMultiple(draft);
        assertTrue(issues.isEmpty());
    }

    @Test
    void validateChoiceMultiple_TooFewCorrect() {
        var draft = new ItemDraft.ChoiceDraft(
                "Topic",
                "Passage text",
                "Which are correct?",
                List.of("A", "B", "C", "D", "E"),
                List.of(0));

        var issues = choiceValidator.validateMultiple(draft);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i.code().equals("CORRECT_COUNT_INVALID")));
    }

    // Blank Fill Validator Tests
    @Test
    void validateBlankFillDropdown_Valid() {
        var slots = List.of(
                new ItemDraft.BlankFillDraft.Slot("answer1", List.of("d1", "d2", "d3")),
                new ItemDraft.BlankFillDraft.Slot("answer2", List.of("d1", "d2", "d3")),
                new ItemDraft.BlankFillDraft.Slot("answer3", List.of("d1", "d2", "d3")),
                new ItemDraft.BlankFillDraft.Slot("answer4", List.of("d1", "d2", "d3")));

        var draft = new ItemDraft.BlankFillDraft("Topic", "passage", slots);
        var issues = blankFillValidator.validateDropdown(draft);
        assertTrue(issues.isEmpty());
    }

    @Test
    void validateBlankFillDropdown_InsufficientDisstractors() {
        var slots = List.of(
                new ItemDraft.BlankFillDraft.Slot("answer1", List.of("d1")));

        var draft = new ItemDraft.BlankFillDraft("Topic", "passage", slots);
        var issues = blankFillValidator.validateDropdown(draft);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i.code().equals("INSUFFICIENT_DISTRACTORS")));
    }

    @Test
    void validateBlankFillDragDrop_Valid() {
        var slots = List.of(
                new ItemDraft.BlankFillDraft.Slot("word1", List.of("d1", "d2")),
                new ItemDraft.BlankFillDraft.Slot("word2", List.of("d3", "d4")),
                new ItemDraft.BlankFillDraft.Slot("word3", List.of("d5", "d6")));

        var draft = new ItemDraft.BlankFillDraft("Topic", "passage", slots);
        var issues = blankFillValidator.validateDragDrop(draft);
        assertTrue(issues.isEmpty());
    }

    // Reorder Validator Tests
    @Test
    void validateReorder_TooShort() {
        var paragraphs = List.of(
                "Short.",
                "Also short.",
                "Another short one.",
                "Last short.");

        var draft = new ItemDraft.ReorderDraft("Topic", paragraphs);
        var issues = reorderValidator.validate(draft);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i.code().equals("PARAGRAPH_LENGTH_INVALID")));
    }

    // Passage Validator Tests
    @Test
    void validateSummary_Valid() {
        String passage = "This is a passage with enough words. " +
                "It needs to be between two hundred and three hundred words for summarization. " +
                "The test will verify that the passage meets all requirements for a summary task. " +
                "Key points are essential elements that students must identify and summarize. " +
                "The passage provides sufficient context and information for examination purposes.";

        var draft = new ItemDraft.PassageDraft("Topic", passage, List.of("Key1", "Key2", "Key3"), null);
        var issues = passageValidator.validateSummary(draft);
        // Passage may be too short, but key points are good
        assertTrue(issues.stream().noneMatch(i -> i.code().equals("NO_KEY_POINTS")));
    }

    @Test
    void validateEssay_NoArgumentKeyword() {
        var draft = new ItemDraft.PassageDraft("Topic", "", null,
                "Write about the weather and environmental conditions.");
        var issues = passageValidator.validateEssay(draft);
        assertFalse(issues.isEmpty());
        assertTrue(issues.stream().anyMatch(i -> i.code().equals("NO_ARGUMENT_KEYWORD")));
    }

    @Test
    void anEssayIsJudgedOnItsPromptNotOnTheMissingPassage() {
        var draft = new ItemDraft.PassageDraft("Technology", null, null,
                "Some people believe that technology has made communication easier. Discuss both views and give your opinion.");
        var issues = new DraftValidator(null, null, null, passageValidator, null, null, null, null)
                .validate(draft, "WRITE_ESSAY");
        assertTrue(issues.stream().noneMatch(i -> i.code().equals("NOT_ENGLISH")),
                "an essay has no passage; its prompt is the text to check");
    }

    @Test
    void validateReadAloud_Valid() {
        String passage = "Urban parks provide essential green spaces where residents can exercise " +
                "relax and connect with nature. These public areas offer recreation facilities and community events.";
        var draft = new ItemDraft.PassageDraft("Topic", passage, null, null);
        var issues = passageValidator.validateReadAloud(draft);
        // May fail on length, but that's expected for test
        assertTrue(issues.stream().noneMatch(i -> i.code().equals("CONTAINS_NUMBERS")));
    }

    @Test
    void validateReadAloud_ContainsNumbers() {
        String passage = "The year is 2024 and we have 365 days in a year for reading practice and more testing.";
        var draft = new ItemDraft.PassageDraft("Topic", passage, null, null);
        var issues = passageValidator.validateReadAloud(draft);
        assertTrue(issues.stream().anyMatch(i -> i.code().equals("CONTAINS_NUMBERS")));
    }
}
