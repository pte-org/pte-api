package com.pte.itembank.internal.generation.validate.audio;

import com.pte.itembank.internal.generation.validate.DraftRuleValidator;
import com.pte.itembank.internal.generation.validate.LengthRules;
import com.pte.itembank.internal.generation.validate.ValidationIssue;

import com.pte.itembank.internal.generation.draft.ItemDraft;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates SUMMARIZE_GROUP_DISCUSSION (SGD) and RESPOND_TO_A_SITUATION (RTS) dialogue drafts.
 * SGD: 3 speakers, 6–12 turns, 180–280 words, each speaker ≥2 turns.
 * RTS: situation 40–90 words, 1–2 turns.
 */
@Component
public class DialogueValidator {

    public List<ValidationIssue> validateSgd(ItemDraft.DialogueDraft draft) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Check speaker count (must have exactly 3 speakers: A, B, C)
        Set<String> speakers = new HashSet<>();
        for (ItemDraft.DialogueDraft.Turn turn : draft.turns()) {
            speakers.add(turn.speaker());
        }

        if (speakers.size() != 3 || !speakers.containsAll(List.of("A", "B", "C"))) {
            issues.add(new ValidationIssue("turns", "SGD must have exactly 3 speakers (A, B, C)"));
        }

        // Check turn count
        int turnCount = draft.turns().size();
        if (turnCount == 0) {
            issues.add(new ValidationIssue("turns", "SGD must have at least one turn"));
        }

        // Check total dialogue word count (180–280 words)
        int totalWords = 0;
        for (ItemDraft.DialogueDraft.Turn turn : draft.turns()) {
            totalWords += AudioCommonValidator.wordCount(turn.text());
        }
        LengthRules sgdRules = LengthRules.forTask("SUMMARIZE_GROUP_DISCUSSION");
        if (!sgdRules.contains(totalWords)) {
            issues.add(new ValidationIssue("length",
                    String.format("SGD total dialogue should be %d–%d words; found %d.",
                            sgdRules.minWords(), sgdRules.maxWords(), totalWords)));
        }

        // Check each speaker has at least one turn
        for (String speaker : List.of("A", "B", "C")) {
            long speakerTurns = draft.turns().stream()
                    .filter(t -> speaker.equals(t.speaker()))
                    .count();
            if (speakerTurns == 0) {
                issues.add(new ValidationIssue("turns", "Speaker " + speaker + " must have at least one turn"));
            }
        }

        return issues;
    }

    public List<ValidationIssue> validateRts(ItemDraft.DialogueDraft draft) {
        List<ValidationIssue> issues = new ArrayList<>();

        // Check situation is present
        if (draft.situation() == null || draft.situation().isBlank()) {
            issues.add(new ValidationIssue("situation", "RTS situation is required"));
            return issues;
        }

        // Check situation length (40–90 words)
        int situationWords = AudioCommonValidator.wordCount(draft.situation());
        LengthRules rtsRules = LengthRules.forTask("RESPOND_TO_A_SITUATION_situation");
        if (!rtsRules.contains(situationWords)) {
            issues.add(new ValidationIssue("situation",
                    String.format("RTS situation should be %d–%d words; found %d.",
                            rtsRules.minWords(), rtsRules.maxWords(), situationWords)));
        }

        // Check turn count is present
        int turnCount = draft.turns() != null ? draft.turns().size() : 0;
        if (turnCount == 0) {
            issues.add(new ValidationIssue("turns", "RTS must have at least one turn"));
        }

        return issues;
    }
}
