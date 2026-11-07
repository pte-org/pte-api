package com.pte.itembank.internal.generation.validate.audio;

import com.pte.itembank.internal.generation.validate.DraftRuleValidator;
import com.pte.itembank.internal.generation.validate.ValidationIssue;

import com.pte.itembank.internal.generation.draft.ItemDraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validator for FILL_IN_THE_BLANKS_TYPE_IN (Listen & Fill in Blanks).
 * Validates that each blank answer is a word from the transcript in correct position.
 */
@org.springframework.stereotype.Component
public class ListenFibValidator implements DraftRuleValidator {

    @Override
    public List<ValidationIssue> validate(ItemDraft draft) {
        if (!(draft instanceof ItemDraft.ListeningDraft listeningDraft)
                || listeningDraft.blanks() == null) {
            return List.of();
        }

        List<ValidationIssue> issues = new ArrayList<>();

        // Common audio rules
        issues.addAll(AudioCommonValidator.validateAudioCommonRules(
                listeningDraft.transcript(), "transcript"));

        String[] transcriptWords = listeningDraft.transcript().split("\\s+");
        Set<String> transcriptWordSet = new HashSet<>();
        for (String word : transcriptWords) {
            transcriptWordSet.add(normalizeWord(word));
        }

        // Validate each blank
        for (int i = 0; i < listeningDraft.blanks().size(); i++) {
            ItemDraft.ListeningDraft.Slot slot = listeningDraft.blanks().get(i);
            String answer = slot.answer();

            // Answer must be from transcript
            if (!transcriptWordSet.contains(normalizeWord(answer))) {
                issues.add(new ValidationIssue("blanks[" + i + "].answer",
                        "Answer must be a word from the transcript"));
            }

            // Answer must not be empty
            if (answer == null || answer.isBlank()) {
                issues.add(new ValidationIssue("blanks[" + i + "].answer", "Answer cannot be empty"));
            }
        }

        return issues;
    }

    private String normalizeWord(String word) {
        // Remove punctuation and lowercase for comparison
        return word.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }
}
