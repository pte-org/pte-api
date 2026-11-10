package com.pte.itembank.internal.generation.validate;

import com.pte.itembank.internal.generation.draft.GapMarkers;
import com.pte.itembank.internal.generation.draft.ItemDraft;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validator for FILL_IN_THE_BLANKS (dropdown and drag-drop).
 * - Dropdown: 4-6 blanks, each with 1 answer + 3 distractors
 * - Drag-drop: 3-5 blanks, word bank = answers + 2-3 extra words
 */
@Component
public class BlankFillValidator {

    private static final int MIN_SLOTS_DROPDOWN = 4;
    private static final int MAX_SLOTS_DROPDOWN = 6;
    private static final int MIN_SLOTS_DRAGDROP = 3;
    private static final int MAX_SLOTS_DRAGDROP = 5;
    private static final int MIN_DISTRACTORS = 3;
    private static final int MIN_EXTRA_WORDS = 2;
    private static final int MAX_EXTRA_WORDS = 3;

    public List<ValidationIssue> validateDropdown(ItemDraft.BlankFillDraft draft) {
        var issues = new ArrayList<ValidationIssue>();

        if (GapMarkers.count(draft.passageWithSlots()) != draft.slots().size()) {
            issues.add(new ValidationIssue("BLANK_MARKER_MISMATCH",
                    "The passage must contain exactly one blank marker for each slot."));
        }

        // Check slot count
        int slotCount = draft.slots().size();
        if (slotCount < MIN_SLOTS_DROPDOWN || slotCount > MAX_SLOTS_DROPDOWN) {
            issues.add(new ValidationIssue(
                    "SLOT_COUNT_INVALID",
                    "Dropdown must have 4-6 blanks."));
        }

        // Validate each slot
        for (int i = 0; i < draft.slots().size(); i++) {
            var slot = draft.slots().get(i);

            if (slot.answer() == null || slot.answer().isBlank()) {
                issues.add(new ValidationIssue(
                        "EMPTY_ANSWER",
                        "Blank " + (i + 1) + " has no correct answer."));
            }

            if (slot.distractors() == null || slot.distractors().size() < MIN_DISTRACTORS) {
                issues.add(new ValidationIssue(
                        "INSUFFICIENT_DISTRACTORS",
                        "Blank " + (i + 1) + " must have at least 3 distractors."));
            }

            // Check distractors are not the same as answer
            if (slot.distractors() != null && slot.answer() != null) {
                for (String distractor : slot.distractors()) {
                    if (slot.answer().equalsIgnoreCase(distractor)) {
                        issues.add(new ValidationIssue(
                                "DISTRACTOR_MATCHES_ANSWER",
                                "Blank " + (i + 1) + " has a distractor matching the answer."));
                        break;
                    }
                }
            }
        }

        return issues;
    }

    public List<ValidationIssue> validateDragDrop(ItemDraft.BlankFillDraft draft) {
        var issues = new ArrayList<ValidationIssue>();

        if (GapMarkers.count(draft.passageWithSlots()) != draft.slots().size()) {
            issues.add(new ValidationIssue("BLANK_MARKER_MISMATCH",
                    "The passage must contain exactly one blank marker for each slot."));
        }

        // Check slot count
        int slotCount = draft.slots().size();
        if (slotCount < MIN_SLOTS_DRAGDROP || slotCount > MAX_SLOTS_DRAGDROP) {
            issues.add(new ValidationIssue(
                    "SLOT_COUNT_INVALID",
                    "Drag-drop must have 3-5 blanks."));
        }

        // Check word bank composition
        var allAnswers = new HashSet<String>();
        for (ItemDraft.BlankFillDraft.Slot slot : draft.slots()) {
            if (slot.answer() != null && !slot.answer().isBlank()) {
                allAnswers.add(slot.answer().toLowerCase());
            }
        }

        // For drag-drop, distractors become the "extra words" in word bank
        int totalExtraWords = 0;
        for (ItemDraft.BlankFillDraft.Slot slot : draft.slots()) {
            if (slot.distractors() != null) {
                totalExtraWords += slot.distractors().size();
            }
        }

        if (totalExtraWords < MIN_EXTRA_WORDS * slotCount ||
            totalExtraWords > MAX_EXTRA_WORDS * slotCount) {
            issues.add(new ValidationIssue(
                    "WORD_BANK_IMBALANCE",
                    "Word bank should have 2-3 extra words per blank."));
        }

        // Validate each slot
        for (int i = 0; i < draft.slots().size(); i++) {
            var slot = draft.slots().get(i);

            if (slot.answer() == null || slot.answer().isBlank()) {
                issues.add(new ValidationIssue(
                        "EMPTY_ANSWER",
                        "Blank " + (i + 1) + " has no correct answer."));
            }

            if (slot.distractors() != null) {
                Set<String> uniqueWords = new HashSet<>(slot.distractors());
                if (uniqueWords.size() != slot.distractors().size()) {
                    issues.add(new ValidationIssue(
                            "DUPLICATE_WORDS_IN_BANK",
                            "Word bank has duplicate words."));
                    break;
                }
            }
        }

        return issues;
    }
}
