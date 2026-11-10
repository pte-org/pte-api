package com.pte.itembank.internal.generation.validate;

import com.pte.itembank.internal.generation.draft.ItemDraft;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validator for MC_READING_SINGLE and MC_READING_MULTIPLE. Rules per phase doc:
 * - MC_SINGLE: 4-5 options, 1 correct, no duplicates
 * - MC_MULTIPLE: 5-7 options, 2-3 correct
 */
@Component
public class ChoiceValidator {

    private static final int MIN_OPTIONS_SINGLE = 4;
    private static final int MAX_OPTIONS_SINGLE = 5;
    private static final int MIN_OPTIONS_MULTIPLE = 5;
    private static final int MAX_OPTIONS_MULTIPLE = 7;

    public List<ValidationIssue> validateSingle(ItemDraft.ChoiceDraft draft) {
        var issues = new ArrayList<ValidationIssue>();

        // Check option count
        int optionCount = draft.options().size();
        if (optionCount < MIN_OPTIONS_SINGLE || optionCount > MAX_OPTIONS_SINGLE) {
            issues.add(new ValidationIssue(
                    "OPTION_COUNT_INVALID",
                    "Single-choice must have 4-5 options."));
        }

        // Check correct answer count: must be exactly 1
        int correctCount = draft.correctIndexes().size();
        if (correctCount != 1) {
            issues.add(new ValidationIssue(
                    "CORRECT_COUNT_INVALID",
                    "Single-choice must have exactly 1 correct option."));
        }

        // Check for valid indices
        for (int idx : draft.correctIndexes()) {
            if (idx < 0 || idx >= optionCount) {
                issues.add(new ValidationIssue(
                        "INVALID_OPTION_INDEX",
                        "Correct answer index out of range."));
                break;
            }
        }

        // Check for duplicate options
        Set<String> unique = new HashSet<>(draft.options());
        if (unique.size() != draft.options().size()) {
            issues.add(new ValidationIssue(
                    "DUPLICATE_OPTIONS",
                    "Options must be distinct."));
        }

        return issues;
    }

    public List<ValidationIssue> validateMultiple(ItemDraft.ChoiceDraft draft) {
        var issues = new ArrayList<ValidationIssue>();

        // Check option count
        int optionCount = draft.options().size();
        if (optionCount < MIN_OPTIONS_MULTIPLE || optionCount > MAX_OPTIONS_MULTIPLE) {
            issues.add(new ValidationIssue(
                    "OPTION_COUNT_INVALID",
                    "Multiple-choice must have 5-7 options."));
        }

        // Check correct answer count: must be 2-3
        int correctCount = draft.correctIndexes().size();
        if (correctCount < 2 || correctCount > 3) {
            issues.add(new ValidationIssue(
                    "CORRECT_COUNT_INVALID",
                    "Multiple-choice must have 2-3 correct options."));
        }

        // Check for valid indices
        for (int idx : draft.correctIndexes()) {
            if (idx < 0 || idx >= optionCount) {
                issues.add(new ValidationIssue(
                        "INVALID_OPTION_INDEX",
                        "Correct answer index out of range."));
                break;
            }
        }

        // Check for duplicate options
        Set<String> unique = new HashSet<>(draft.options());
        if (unique.size() != draft.options().size()) {
            issues.add(new ValidationIssue(
                    "DUPLICATE_OPTIONS",
                    "Options must be distinct."));
        }

        return issues;
    }
}
