package com.pte.scoring.internal.rubric.speech;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.speech.TokenAlignment;

import java.util.List;

/**
 * The words of a reference or of what was heard, tokenised like {@link TokenAlignment}. Hesitation words are dropped
 * from what was heard (Score Guide p.16: hesitations and filled pauses are ignored when Content is counted).
 */
final class SpokenWords {

    private static final String SEPARATOR = " ";

    private SpokenWords() {
    }

    static List<String> reference(String text) {
        return TokenAlignment.normalize(text);
    }

    static List<String> heard(String text) {
        return TokenAlignment.normalize(text).stream()
                .filter(word -> !ScoringConstants.SPEECH_FILLER_WORDS.contains(word)).toList();
    }

    /** Re-joined tokens normalise back to themselves, so the alignment sees exactly these words. */
    static String join(List<String> words) {
        return String.join(SEPARATOR, words);
    }
}
