package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.internal.generation.dto.GenerationMetaDto;
import org.springframework.stereotype.Component;

/**
 * Calculates readability metrics from text: word count, average sentence length, and Flesch-Kincaid grade level.
 * Used to populate generation_meta.metrics for audit and analysis purposes.
 */
@Component
public class MetricsCalculator {

    /**
     * Calculates metrics from the given text (passage or stimulus).
     */
    public GenerationMetaDto.GenerationMetricsDto calculate(String text) {
        if (text == null || text.isBlank()) {
            return new GenerationMetaDto.GenerationMetricsDto(0, 0.0, 0.0);
        }

        int wordCount = countWords(text);
        if (wordCount == 0) {
            return new GenerationMetaDto.GenerationMetricsDto(0, 0.0, 0.0);
        }

        int sentenceCount = countSentences(text);
        if (sentenceCount == 0) {
            sentenceCount = 1;  // At least one sentence if there are words
        }

        int syllableCount = countSyllables(text);

        double avgSentenceLength = (double) wordCount / sentenceCount;
        double fleschKincaid = calculateFleschKincaid(wordCount, sentenceCount, syllableCount);

        return new GenerationMetaDto.GenerationMetricsDto(
                wordCount,
                Math.round(avgSentenceLength * 10.0) / 10.0,
                Math.round(fleschKincaid * 10.0) / 10.0);
    }

    /**
     * Counts words by splitting on whitespace and filtering non-empty tokens.
     */
    private int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String[] words = text.trim().split("\\s+");
        return (int) java.util.Arrays.stream(words)
                .filter(word -> !word.isBlank())
                .count();
    }

    /**
     * Counts sentences by splitting on sentence-ending punctuation: . ! ? followed by space or end.
     */
    private int sentenceCount;

    private int countSentences(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        // Count sentence-ending punctuation marks
        int count = 0;
        boolean prevWasPunct = false;
        for (char c : text.toCharArray()) {
            if (c == '.' || c == '!' || c == '?') {
                if (!prevWasPunct) {
                    count++;
                    prevWasPunct = true;
                }
            } else if (!Character.isWhitespace(c)) {
                prevWasPunct = false;
            }
        }
        return Math.max(count, 1);  // At least 1 sentence
    }

    /**
     * Counts syllables using a simple heuristic: vowel groups + silent e adjustments.
     * This is an approximation; accurate syllable counting requires a dictionary.
     */
    private int countSyllables(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }

        int syllables = 0;
        boolean previousWasVowel = false;

        for (char c : text.toLowerCase().toCharArray()) {
            boolean isVowel = "aeiouy".indexOf(c) >= 0;
            if (isVowel && !previousWasVowel) {
                syllables++;
            }
            previousWasVowel = isVowel;
        }

        // Adjust for silent 'e' at the end
        if (text.toLowerCase().endsWith("e")) {
            syllables--;
        }

        // Adjust for 'le' at the end
        if (text.toLowerCase().endsWith("le") && text.length() > 2) {
            char thirdFromEnd = text.charAt(text.length() - 3);
            boolean isConsonant = "aeiouy".indexOf(thirdFromEnd) < 0;
            if (isConsonant) {
                syllables++;
            }
        }

        return Math.max(syllables, 1);  // At least 1 syllable per word
    }

    /**
     * Calculates Flesch-Kincaid Grade Level: 0.39 * (words / sentences) + 11.8 * (syllables / words) - 15.59
     */
    private double calculateFleschKincaid(int words, int sentences, int syllables) {
        if (words == 0 || sentences == 0) {
            return 0.0;
        }
        double grade = 0.39 * (words / (double) sentences) +
                11.8 * (syllables / (double) words) -
                15.59;
        return Math.max(0.0, grade);
    }
}
