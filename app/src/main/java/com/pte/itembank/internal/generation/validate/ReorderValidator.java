package com.pte.itembank.internal.generation.validate;

import com.pte.itembank.internal.generation.draft.ItemDraft;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Validator for RE_ORDER_PARAGRAPHS: 4-5 paragraphs, each 15-60 words.
 */
@Component
public class ReorderValidator {

    private static final int MIN_PARAGRAPHS = 4;
    private static final int MAX_PARAGRAPHS = 5;
    private static final int MIN_WORDS_PER_PARAGRAPH = 15;
    private static final int MAX_WORDS_PER_PARAGRAPH = 60;

    public List<ValidationIssue> validate(ItemDraft.ReorderDraft draft) {
        var issues = new ArrayList<ValidationIssue>();

        // Check paragraph count
        int count = draft.paragraphs().size();
        if (count < MIN_PARAGRAPHS || count > MAX_PARAGRAPHS) {
            issues.add(new ValidationIssue(
                    "PARAGRAPH_COUNT_INVALID",
                    "Reorder paragraphs task must have 4-5 paragraphs."));
        }

        // Check each paragraph
        for (int i = 0; i < draft.paragraphs().size(); i++) {
            String para = draft.paragraphs().get(i);
            if (para == null || para.isBlank()) {
                issues.add(new ValidationIssue(
                        "EMPTY_PARAGRAPH",
                        "Paragraph " + (i + 1) + " is empty."));
                continue;
            }

            int wordCount = countWords(para);
            if (wordCount < MIN_WORDS_PER_PARAGRAPH || wordCount > MAX_WORDS_PER_PARAGRAPH) {
                issues.add(new ValidationIssue(
                        "PARAGRAPH_LENGTH_INVALID",
                        "Paragraph " + (i + 1) + " should be 15-60 words; found " + wordCount + "."));
            }
        }

        return issues;
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}
