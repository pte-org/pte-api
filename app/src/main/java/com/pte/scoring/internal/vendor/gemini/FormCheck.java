package com.pte.scoring.internal.vendor.gemini;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.rubric.FormRule;
import com.pte.scoring.rubric.TextMetrics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Counts what the Form criterion of a written response depends on (words, sentences, capitals, punctuation, bullet
 * points), in code and never by the model, because LLMs miscount. It decides nothing: the band comes from the
 * rubric's {@link FormRule#bandFor}. Ported from the POC's {@code rules.py}.
 */
public final class FormCheck {

    /** A sentence of at most this many words is "very short"; a response made only of such sentences is bullet-like. */
    private static final int VERY_SHORT_SENTENCE_MAX = 3;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    // U.S.A., e.g., a.m.: a chain of single letters and dots is one token, whatever follows it.
    private static final Pattern ACRONYM = Pattern.compile("\\b(?:\\p{L}\\.){2,}");
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?]+(?:\\s+|$)");
    private static final Pattern PUNCTUATION = Pattern.compile("[.,;:!?]");
    private static final Pattern BULLET_LINE = Pattern.compile("^\\s*(?:[-*\\u2022\\u2023\\u25AA\\u25E6]|\\d+[.)])\\s+");
    // Abbreviations whose full stop must not end a sentence.
    private static final List<String> ABBREVIATIONS = List.of(
            "Mr.", "Mrs.", "Ms.", "Dr.", "Prof.", "Sr.", "Jr.",
            "e.g.", "i.e.", "etc.", "vs.", "U.S.", "U.K.", "U.N.",
            "Inc.", "Ltd.", "Co.", "approx.", "cf.", "et al.");

    private FormCheck() {
    }

    public static TextMetrics measure(String text) {
        String stripped = text == null ? "" : text.strip();
        return new TextMetrics(countWords(stripped), countSentences(stripped), isAllCaps(stripped),
                PUNCTUATION.matcher(stripped).find(), isBulletOnly(stripped));
    }

    /** The counts, the band the rubric gave and the rule it applied; never the text itself. */
    public static Map<String, Object> detail(TextMetrics metrics, FormRule rule, int band) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put(ScoringConstants.DETAIL_WORD_COUNT, metrics.wordCount());
        detail.put("sentenceCount", metrics.sentenceCount());
        detail.put("allCaps", metrics.allCaps());
        detail.put("hasPunctuation", metrics.hasPunctuation());
        detail.put("bulletOnly", metrics.bulletOnly());
        detail.put("band", band);
        detail.put("maxBand", rule.maxBand());
        detail.put("requireSingleSentence", rule.requireSingleSentence());
        detail.put("mechanicalRules", rule.applyMechanicalRules());
        return detail;
    }

    static int countWords(String text) {
        String stripped = text == null ? "" : text.strip();
        return stripped.isEmpty() ? 0 : WHITESPACE.split(stripped).length;
    }

    /** A heuristic, not an NLP splitter: unlisted abbreviations can still overcount. */
    static int countSentences(String text) {
        return sentences(text).size();
    }

    /**
     * The text cut at sentence ends, abbreviations and acronyms protected. A text without any closing punctuation is
     * one sentence; an empty text has none.
     */
    private static List<String> sentences(String text) {
        String stripped = text == null ? "" : text.strip();
        if (stripped.isEmpty()) {
            return List.of();
        }
        // Acronym chains first: the abbreviation list would otherwise eat "U.S." of "U.S.A." and strand the "A.".
        String protectedText = ACRONYM.matcher(stripped).replaceAll("__ACR__");
        for (int i = 0; i < ABBREVIATIONS.size(); i++) {
            protectedText = protectedText.replace(ABBREVIATIONS.get(i), "__ABBR" + i + "__");
        }
        List<String> sentences = new ArrayList<>();
        Matcher end = SENTENCE_END.matcher(protectedText + " ");
        int start = 0;
        while (end.find()) {
            sentences.add(protectedText.substring(start, Math.min(end.start(), protectedText.length())));
            start = Math.min(end.end(), protectedText.length());
        }
        if (sentences.isEmpty()) {
            sentences.add(protectedText);
        }
        return sentences;
    }

    /** Letters are present and none of them is lower case. */
    private static boolean isAllCaps(String text) {
        boolean hasLetter = false;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (Character.isLowerCase(character)) {
                return false;
            }
            hasLetter |= Character.isLetter(character);
        }
        return hasLetter;
    }

    /** Every non-blank line is a bullet or list item, or the text is two or more very short sentences only. */
    private static boolean isBulletOnly(String text) {
        List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();
        if (!lines.isEmpty() && lines.stream().allMatch(line -> BULLET_LINE.matcher(line).find())) {
            return true;
        }
        List<String> sentences = sentences(text);
        return sentences.size() > 1 && sentences.stream().allMatch(sentence -> countWords(sentence)
                <= VERY_SHORT_SENTENCE_MAX);
    }
}
