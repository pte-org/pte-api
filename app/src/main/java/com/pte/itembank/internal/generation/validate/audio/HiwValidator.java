package com.pte.itembank.internal.generation.validate.audio;

import com.pte.itembank.internal.generation.validate.DraftRuleValidator;
import com.pte.itembank.internal.generation.validate.ValidationIssue;

import com.pte.itembank.internal.generation.draft.ItemDraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validator for Highlight Incorrect Words (HIW).
 * Validates that displayedText matches transcript structure with changed words.
 */
@org.springframework.stereotype.Component
public class HiwValidator implements DraftRuleValidator {

    @Override
    public List<ValidationIssue> validate(ItemDraft draft) {
        if (!(draft instanceof ItemDraft.HiwDraft hiwDraft)) {
            return List.of();
        }

        List<ValidationIssue> issues = new ArrayList<>();

        // Common audio rules
        issues.addAll(AudioCommonValidator.validateAudioCommonRules(hiwDraft.transcript(), "transcript"));

        String[] transcriptWords = hiwDraft.transcript().split("\\s+");
        String[] displayedWords = hiwDraft.displayedText().split("\\s+");

        // Same number of words
        if (transcriptWords.length != displayedWords.length) {
            issues.add(new ValidationIssue("displayedText",
                    "Displayed text must have the same number of words as transcript"));
        }

        // Validate changed indexes
        int[] changedIndexes = hiwDraft.changedIndexes();
        if (changedIndexes == null || changedIndexes.length < 4 || changedIndexes.length > 7) {
            issues.add(new ValidationIssue("changedIndexes",
                    "Must change between 4 and 7 words"));
        }

        // Validate that changed words are actually different and non-homophone
        if (changedIndexes != null) {
            for (int idx : changedIndexes) {
                if (idx < 0 || idx >= Math.min(transcriptWords.length, displayedWords.length)) {
                    issues.add(new ValidationIssue("changedIndexes", "Index out of range: " + idx));
                    continue;
                }

                String originalWord = transcriptWords[idx];
                String changedWord = displayedWords[idx];

                if (originalWord.equalsIgnoreCase(changedWord)) {
                    issues.add(new ValidationIssue("changedIndexes",
                            "Word at index " + idx + " must be different"));
                }

                // Check spelling difference (at least 2 characters different)
                int differences = countCharDifferences(originalWord, changedWord);
                if (differences < 2) {
                    issues.add(new ValidationIssue("changedIndexes",
                            "Word at index " + idx + " must differ by at least 2 characters"));
                }

                // Check first 2 characters must be different (to avoid homophones/near homophones)
                if (originalWord.length() >= 2 && changedWord.length() >= 2) {
                    if (originalWord.substring(0, 2).equalsIgnoreCase(changedWord.substring(0, 2))) {
                        issues.add(new ValidationIssue("changedIndexes",
                                "Word at index " + idx + " must have different first two characters"));
                    }
                }
            }
        }

        return issues;
    }

    private int countCharDifferences(String word1, String word2) {
        String w1 = word1.toLowerCase();
        String w2 = word2.toLowerCase();
        int minLen = Math.min(w1.length(), w2.length());
        int differences = Math.abs(w1.length() - w2.length());

        for (int i = 0; i < minLen; i++) {
            if (w1.charAt(i) != w2.charAt(i)) {
                differences++;
            }
        }

        return differences;
    }
}
