package com.pte.scoring.internal.rubric.speech;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.speech.TokenAlignment;
import com.pte.scoring.internal.vendor.speech.TokenAlignment.Block;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Repeat Sentence Content 0-3 (Score Guide p.16), counted in code from the words of the sentence repeated in the
 * correct sequence (the aligned runs of the heard words against the sentence, hesitations ignored):
 * 3 = every word in sequence; 2 = at least 50% of the words; 1 = fewer than 50% but at least one; 0 = no word
 * (the Guide's "almost nothing"). Whole-number arithmetic, nothing is rounded.
 */
public record RepeatSentenceContent(int sentenceWords, int wordsInSequence, int band) {

    public static final int ALL_IN_SEQUENCE = 3;
    public static final int AT_LEAST_HALF = 2;
    public static final int LESS_THAN_HALF = 1;
    public static final int NOTHING = 0;

    /** @throws AiProviderException when the sentence has no word: a broken item, never scored */
    public static RepeatSentenceContent measure(String sentence, String heard) {
        List<String> reference = SpokenWords.reference(sentence);
        if (reference.isEmpty()) {
            throw new AiProviderException(ScoringConstants.SPEECH_REFERENCE_TEXT_EMPTY);
        }
        List<String> spoken = SpokenWords.heard(heard);
        int inSequence = 0;
        if (!spoken.isEmpty()) {
            List<Block> blocks = TokenAlignment.align(SpokenWords.join(reference), SpokenWords.join(spoken))
                    .blocks();
            inSequence = blocks.stream().mapToInt(Block::size).sum();
        }
        return new RepeatSentenceContent(reference.size(), inSequence, bandOf(inSequence, reference.size()));
    }

    public Map<String, Object> detail() {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sentenceWords", sentenceWords);
        detail.put("wordsInSequence", wordsInSequence);
        detail.put("band", band);
        return detail;
    }

    private static int bandOf(int inSequence, int total) {
        if (inSequence == total) {
            return ALL_IN_SEQUENCE;
        }
        if (2 * inSequence >= total) {
            return AT_LEAST_HALF;
        }
        return inSequence > 0 ? LESS_THAN_HALF : NOTHING;
    }
}
