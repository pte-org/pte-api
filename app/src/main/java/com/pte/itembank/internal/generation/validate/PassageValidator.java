package com.pte.itembank.internal.generation.validate;

import com.pte.itembank.internal.generation.validate.audio.*;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validator for passage-based tasks: SUMMARIZE_WRITTEN_TEXT, WRITE_ESSAY, READ_ALOUD.
 * Validates structural constraints (presence, key point count, argumentative keywords, etc.).
 * Word-count ranges are validated using LengthRules to avoid triggering RubricScoreSingleSourceTest.
 */
@Component
public class PassageValidator {

    private static final int MIN_KEY_POINTS = 3;
    private static final int MAX_KEY_POINTS = 4;

    private static final String[] ARGUMENT_KEYWORDS = {
            "agree", "disagree", "discuss", "advantages", "disadvantages",
            "benefits", "drawbacks", "extent", "opinion", "debate"
    };

    public List<ValidationIssue> validateSummary(ItemDraft.PassageDraft draft) {
        var issues = new ArrayList<ValidationIssue>();

        // Check passage is present
        if (draft.passage() == null || draft.passage().isBlank()) {
            issues.add(new ValidationIssue(
                    "MISSING_PASSAGE",
                    "SWT must have a passage."));
            return issues;
        }

        // Check passage length (200–300 words)
        int passageWordCount = AudioCommonValidator.wordCount(draft.passage());
        LengthRules swtRules = LengthRules.forTask("SUMMARIZE_WRITTEN_TEXT");
        if (!swtRules.contains(passageWordCount)) {
            issues.add(new ValidationIssue(
                    "PASSAGE_LENGTH_INVALID",
                    String.format("SWT passage should be %d–%d words; found %d.",
                            swtRules.minWords(), swtRules.maxWords(), passageWordCount)));
        }

        // Check key points
        if (draft.keyPoints() == null || draft.keyPoints().isEmpty()) {
            issues.add(new ValidationIssue(
                    "NO_KEY_POINTS",
                    "SWT must have key points for the summary."));
        } else if (draft.keyPoints().size() < MIN_KEY_POINTS || draft.keyPoints().size() > MAX_KEY_POINTS) {
            issues.add(new ValidationIssue(
                    "KEY_POINTS_COUNT_INVALID",
                    "SWT should have 3-4 key points; found " + draft.keyPoints().size() + "."));
        }

        // Essay prompt should be null
        if (draft.essayPrompt() != null && !draft.essayPrompt().isBlank()) {
            issues.add(new ValidationIssue(
                    "UNEXPECTED_ESSAY_PROMPT",
                    "SWT should not have an essay prompt."));
        }

        return issues;
    }

    public List<ValidationIssue> validateEssay(ItemDraft.PassageDraft draft) {
        var issues = new ArrayList<ValidationIssue>();

        // Check essay prompt is present
        if (draft.essayPrompt() == null || draft.essayPrompt().isBlank()) {
            issues.add(new ValidationIssue(
                    "MISSING_ESSAY_PROMPT",
                    "Essay task must have a prompt."));
            return issues;
        }

        // Check prompt length (20–60 words)
        int promptWordCount = AudioCommonValidator.wordCount(draft.essayPrompt());
        LengthRules weRules = LengthRules.forTask("WRITE_ESSAY");
        if (!weRules.contains(promptWordCount)) {
            issues.add(new ValidationIssue(
                    "PROMPT_LENGTH_INVALID",
                    String.format("Essay prompt should be %d–%d words; found %d.",
                            weRules.minWords(), weRules.maxWords(), promptWordCount)));
        }

        // Check for argumentative keywords
        String lowerPrompt = draft.essayPrompt().toLowerCase();
        boolean hasKeyword = false;
        for (String keyword : ARGUMENT_KEYWORDS) {
            if (lowerPrompt.contains(keyword)) {
                hasKeyword = true;
                break;
            }
        }

        if (!hasKeyword) {
            issues.add(new ValidationIssue(
                    "NO_ARGUMENT_KEYWORD",
                    "Essay prompt should include argumentative keywords (e.g., agree, discuss, advantages)."));
        }

        // Key points and passage should be minimal
        if (draft.keyPoints() != null && !draft.keyPoints().isEmpty()) {
            issues.add(new ValidationIssue(
                    "UNEXPECTED_KEY_POINTS",
                    "Essay should not have key points."));
        }

        if (draft.passage() != null && !draft.passage().isBlank()) {
            issues.add(new ValidationIssue(
                    "UNEXPECTED_PASSAGE",
                    "Essay should not have a passage context."));
        }

        return issues;
    }

    public List<ValidationIssue> validateReadAloud(ItemDraft.PassageDraft draft) {
        var issues = new ArrayList<ValidationIssue>();

        // Check passage is present
        if (draft.passage() == null || draft.passage().isBlank()) {
            issues.add(new ValidationIssue(
                    "MISSING_PASSAGE",
                    "Read Aloud must have a passage."));
            return issues;
        }

        // Check passage length (50–60 words)
        int passageWordCount = AudioCommonValidator.wordCount(draft.passage());
        LengthRules raRules = LengthRules.forTask("READ_ALOUD");
        if (!raRules.contains(passageWordCount)) {
            issues.add(new ValidationIssue(
                    "PASSAGE_LENGTH_INVALID",
                    String.format("Read Aloud passage should be %d–%d words; found %d.",
                            raRules.minWords(), raRules.maxWords(), passageWordCount)));
        }

        // Check for numbers and abbreviations
        if (containsNumbers(draft.passage())) {
            issues.add(new ValidationIssue(
                    "CONTAINS_NUMBERS",
                    "Read Aloud passage should not contain numbers; write them as words."));
        }

        if (containsAbbreviations(draft.passage())) {
            issues.add(new ValidationIssue(
                    "CONTAINS_ABBREVIATIONS",
                    "Read Aloud passage should avoid abbreviations."));
        }

        // Key points and essay prompt should be null
        if ((draft.keyPoints() != null && !draft.keyPoints().isEmpty()) ||
            (draft.essayPrompt() != null && !draft.essayPrompt().isBlank())) {
            issues.add(new ValidationIssue(
                    "UNEXPECTED_EXTRA_FIELDS",
                    "Read Aloud should only have a passage."));
        }

        return issues;
    }

    private boolean containsNumbers(String text) {
        return text.matches(".*\\d.*");
    }

    private boolean containsAbbreviations(String text) {
        // Simple heuristic: detect uppercase sequences of 2+ letters followed by period
        return text.matches(".*\\b[A-Z]{2,}\\.\\b.*");
    }
}
