package com.pte.itembank.internal.generation.validate.audio;

import com.pte.itembank.internal.generation.validate.DraftRuleValidator;
import com.pte.itembank.internal.generation.validate.ValidationIssue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Common validation rules for all audio task types.
 * Enforces no digits (must be spelled out) and no uppercase abbreviations.
 */
public class AudioCommonValidator {

    private static final Pattern CONTAINS_DIGIT = Pattern.compile("\\d");
    private static final Pattern UPPERCASE_ABBREV = Pattern.compile("\\b[A-Z]{2,}\\b");

    public static List<ValidationIssue> validateAudioCommonRules(String text, String fieldName) {
        List<ValidationIssue> issues = new ArrayList<>();

        if (CONTAINS_DIGIT.matcher(text).find()) {
            issues.add(new ValidationIssue(fieldName, "Numbers must be written as words, not digits"));
        }

        if (UPPERCASE_ABBREV.matcher(text).find()) {
            issues.add(new ValidationIssue(fieldName, "Avoid uppercase abbreviations"));
        }

        return issues;
    }

    public static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}
