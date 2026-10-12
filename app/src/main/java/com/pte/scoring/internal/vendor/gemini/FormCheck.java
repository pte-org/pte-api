package com.pte.scoring.internal.vendor.gemini;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The writing tasks' mechanical form rules, counted in code and never by the
 * model (LLMs miscount). A failed check scores Form zero; {@code status} says
 * how far outside the range the text was. Ported from the POC's {@code rules.py}.
 */
record FormCheck(int wordCount, Integer sentenceCount, boolean passed, String status, String message) {

    static final String IN_RANGE = "in_range";
    static final String OUTSIDE_FULL_RANGE = "outside_full_range";
    static final String INVALID_BOUNDARY = "invalid_boundary";

    private static final int SWT_MIN_WORDS = 5;
    private static final int SWT_MAX_WORDS = 75;
    private static final int ESSAY_MIN_WORDS = 200;
    private static final int ESSAY_MAX_WORDS = 300;
    private static final int SST_MIN_WORDS = 50;
    private static final int SST_MAX_WORDS = 70;
    private static final int SST_INVALID_BELOW = 40;
    private static final int SST_INVALID_ABOVE = 100;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    // U.S.A., e.g., a.m.: a chain of single letters and dots is one token, whatever follows it.
    private static final Pattern ACRONYM = Pattern.compile("\\b(?:\\p{L}\\.){2,}");
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]+(?:\\s+|$)");
    // Abbreviations whose full stop must not end a sentence.
    private static final List<String> ABBREVIATIONS = List.of(
            "Mr.", "Mrs.", "Ms.", "Dr.", "Prof.", "Sr.", "Jr.",
            "e.g.", "i.e.", "etc.", "vs.", "U.S.", "U.K.", "U.N.",
            "Inc.", "Ltd.", "Co.", "approx.", "cf.", "et al.");

    static FormCheck summarizeWrittenText(String text, Integer minWords, Integer maxWords) {
        int words = countWords(text);
        int sentences = countSentences(text);
        int min = limit(minWords, SWT_MIN_WORDS);
        int max = limit(maxWords, SWT_MAX_WORDS);
        StringBuilder issues = new StringBuilder();
        if (words < min || words > max) {
            issues.append("word count ").append(words).append(" outside ").append(min).append('-').append(max);
        }
        if (sentences != 1) {
            issues.append(issues.isEmpty() ? "" : "; ").append("sentence count ").append(sentences).append(" != 1");
        }
        boolean passed = issues.isEmpty();
        return new FormCheck(words, sentences, passed, passed ? IN_RANGE : OUTSIDE_FULL_RANGE,
                passed ? "OK" : issues.toString());
    }

    static FormCheck essay(String text, Integer minWords, Integer maxWords) {
        int words = countWords(text);
        int min = limit(minWords, ESSAY_MIN_WORDS);
        int max = limit(maxWords, ESSAY_MAX_WORDS);
        boolean passed = words >= min && words <= max;
        return new FormCheck(words, null, passed, passed ? IN_RANGE : OUTSIDE_FULL_RANGE,
                passed ? "OK" : "word count " + words + " outside " + min + "-" + max);
    }

    /**
     * Inside the range is full form; far outside it ({@code invalid_boundary})
     * is the POC's explicit zero-form boundary. The question's own range moves
     * the full-form window but never narrows the boundary.
     */
    static FormCheck summarizeSpokenText(String text, Integer minWords, Integer maxWords) {
        int words = countWords(text);
        int min = limit(minWords, SST_MIN_WORDS);
        int max = limit(maxWords, SST_MAX_WORDS);
        if (words >= min && words <= max) {
            return new FormCheck(words, null, true, IN_RANGE, "OK");
        }
        boolean invalid = words < Math.min(SST_INVALID_BELOW, min) || words > Math.max(SST_INVALID_ABOVE, max);
        return new FormCheck(words, null, false, invalid ? INVALID_BOUNDARY : OUTSIDE_FULL_RANGE,
                "word count " + words + " outside " + min + "-" + max);
    }

    static int countWords(String text) {
        String stripped = text == null ? "" : text.strip();
        return stripped.isEmpty() ? 0 : WHITESPACE.split(stripped).length;
    }

    /** A heuristic, not an NLP splitter: unlisted abbreviations can still overcount. */
    static int countSentences(String text) {
        String stripped = text == null ? "" : text.strip();
        if (stripped.isEmpty()) {
            return 0;
        }
        // Acronym chains first: the abbreviation list would otherwise eat "U.S." of "U.S.A." and strand the "A.".
        String protectedText = ACRONYM.matcher(stripped).replaceAll("__ACR__");
        for (int i = 0; i < ABBREVIATIONS.size(); i++) {
            protectedText = protectedText.replace(ABBREVIATIONS.get(i), "__ABBR" + i + "__");
        }
        long ends = SENTENCE_END.matcher(protectedText + " ").results().count();
        return ends == 0 ? 1 : (int) ends;
    }

    /** Form is all or nothing: a failed check is worth zero. */
    int score() {
        return passed ? 100 : 0;
    }

    Map<String, Object> detail() {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("wordCount", wordCount);
        detail.put("sentenceCount", sentenceCount);
        detail.put("passed", passed);
        detail.put("status", status);
        detail.put("message", message);
        return detail;
    }

    private static int limit(Integer questionLimit, int fallback) {
        return questionLimit != null && questionLimit > 0 ? questionLimit : fallback;
    }
}
