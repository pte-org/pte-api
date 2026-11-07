package com.pte.itembank.internal.generation.validate.audio;

import com.pte.itembank.internal.generation.validate.DraftRuleValidator;
import com.pte.itembank.internal.generation.validate.ValidationIssue;

import com.pte.itembank.internal.generation.draft.ItemDraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validator for listening-based audio tasks.
 */
public class ListeningValidator implements DraftRuleValidator {

    @Override
    public List<ValidationIssue> validate(ItemDraft draft) {
        if (!(draft instanceof ItemDraft.ListeningDraft listeningDraft)) {
            return List.of();
        }

        List<ValidationIssue> issues = new ArrayList<>();

        // Common audio rules
        issues.addAll(AudioCommonValidator.validateAudioCommonRules(
                listeningDraft.transcript(), "transcript"));

        // Validate transcript length per type
        int wordCount = AudioCommonValidator.wordCount(listeningDraft.transcript());

        // This validator is generic; specific type validation happens in type-specific validators
        if (wordCount == 0) {
            issues.add(new ValidationIssue("transcript", "Transcript cannot be empty"));
        }

        return issues;
    }
}
