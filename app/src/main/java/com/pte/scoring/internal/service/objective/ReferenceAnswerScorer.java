package com.pte.scoring.internal.service.objective;

import com.pte.attempt.dto.response.SubmittedAnswerView;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Scores objective answers against a frozen textual reference (typed gaps, highlighted words, dictation). */
class ReferenceAnswerScorer {

    private final JsonMapper jsonMapper;

    ReferenceAnswerScorer(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * Listening fill-blanks use a positional JSON string array in
     * {@code correctAnswerText}; the submitted payload is the same positions
     * joined with commas. A malformed reference is unscorable and therefore
     * fails closed at zero instead of accidentally awarding a perfect score.
     */
    int scoreListeningFillBlanks(SubmittedAnswerView answer) {
        List<String> expected = parseListeningFillBlankReference(answer.correctAnswerText());
        if (expected.isEmpty()) {
            return 0;
        }
        List<String> submitted = parseListeningFillBlankPayload(answer.payload());
        int correctGaps = 0;
        for (int i = 0; i < expected.size(); i++) {
            if (i < submitted.size() && expected.get(i).equals(submitted.get(i))) {
                correctGaps++;
            }
        }
        return Math.round(100f * correctGaps / expected.size());
    }

    /**
     * Highlight-incorrect-words uses zero-based transcript token positions.
     * Pearson's rule is +1 for each expected selection and -1 for each
     * unexpected selection, floored at zero for the item.
     */
    int scoreHighlightIncorrectWords(SubmittedAnswerView answer) {
        List<Integer> expected = parseHighlightIncorrectReference(answer.correctAnswerText());
        if (expected.isEmpty()) {
            return 0;
        }
        Set<Integer> expectedIndexes = new HashSet<>(expected);
        Set<Integer> submitted = OrderIndexPayload.parsePayloadAsOrderIndexSet(answer.payload());
        int correctSelections = 0;
        int incorrectSelections = 0;
        for (Integer selected : submitted) {
            if (expectedIndexes.contains(selected)) {
                correctSelections++;
            } else {
                incorrectSelections++;
            }
        }
        int points = Math.max(correctSelections - incorrectSelections, 0);
        return Math.round(100f * points / expectedIndexes.size());
    }

    /**
     * Write-from-dictation awards partial credit by the number of reference
     * words preserved in order. Comparison is case-insensitive and removes
     * punctuation at token boundaries, while the LCS prevents reordered or duplicated words from
     * being counted as correct positions.
     */
    int scoreWriteFromDictation(SubmittedAnswerView answer) {
        List<String> expected = tokenizeDictation(answer.correctAnswerText());
        if (expected.isEmpty()) {
            return 0;
        }
        List<String> submitted = tokenizeDictation(answer.payload());
        int matchedWords = longestCommonSubsequenceLength(expected, submitted);
        return Math.round(100f * matchedWords / expected.size());
    }

    private List<String> parseListeningFillBlankReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = jsonMapper.readValue(reference, new TypeReference<List<String>>() {
            });
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            for (String value : values) {
                if (value == null || value.isBlank() || value.contains(",")) {
                    return List.of();
                }
            }
            return List.copyOf(values);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Integer> parseHighlightIncorrectReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return List.of();
        }
        try {
            List<Integer> values = jsonMapper.readValue(reference, new TypeReference<List<Integer>>() {
            });
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            Set<Integer> seen = new HashSet<>();
            for (Integer value : values) {
                if (value == null || value < 0 || !seen.add(value)) {
                    return List.of();
                }
            }
            return List.copyOf(values);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> parseListeningFillBlankPayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : payload.split(",", -1)) {
            result.add(normalizeGapValue(value));
        }
        return result;
    }

    private String normalizeGapValue(String value) {
        return value == null
                ? ""
                : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private List<String> tokenizeDictation(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String rawToken : value.toLowerCase(Locale.ROOT).split("\\s+")) {
            String token = rawToken.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "");
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        return result;
    }

    private int longestCommonSubsequenceLength(List<String> expected, List<String> submitted) {
        int[] previous = new int[submitted.size() + 1];
        for (String expectedWord : expected) {
            int[] current = new int[submitted.size() + 1];
            for (int j = 1; j <= submitted.size(); j++) {
                if (expectedWord.equals(submitted.get(j - 1))) {
                    current[j] = previous[j - 1] + 1;
                } else {
                    current[j] = Math.max(previous[j], current[j - 1]);
                }
            }
            previous = current;
        }
        return previous[submitted.size()];
    }
}
