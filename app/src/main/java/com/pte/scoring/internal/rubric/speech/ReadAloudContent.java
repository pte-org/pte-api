package com.pte.scoring.internal.rubric.speech;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.speech.TokenAlignment;
import com.pte.scoring.internal.vendor.speech.TokenAlignment.Block;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read Aloud Content (Score Guide pp.15-16), counted in code: the maximum is N, the number of words in the passage,
 * and every replaced, omitted or inserted word is one error, so Content = max(0, N - errors). Hesitation words are
 * ignored. Errors come from the word alignment of what was heard against the passage: between two aligned runs the
 * unmatched passage words and heard words pair up as replacements, and the rest are omissions or insertions.
 *
 * @param wordCount N, stored with the trait scores so the item can be rescored without the passage
 */
public record ReadAloudContent(int wordCount, int achieved, int replacements, int omissions, int insertions) {

    /** @throws AiProviderException when the passage has no word: a broken item, never scored */
    public static ReadAloudContent measure(String passage, String heard) {
        List<String> reference = SpokenWords.reference(passage);
        if (reference.isEmpty()) {
            throw new AiProviderException(ScoringConstants.SPEECH_REFERENCE_TEXT_EMPTY);
        }
        List<String> spoken = SpokenWords.heard(heard);
        if (spoken.isEmpty()) {
            return new ReadAloudContent(reference.size(), 0, 0, reference.size(), 0);
        }
        List<Block> blocks = TokenAlignment.align(SpokenWords.join(reference), SpokenWords.join(spoken)).blocks();
        int replacements = 0;
        int omissions = 0;
        int insertions = 0;
        int referencePosition = 0;
        int spokenPosition = 0;
        for (Block block : blocks) {
            int[] gap = gap(block.referenceStart() - referencePosition, block.candidateStart() - spokenPosition);
            replacements += gap[0];
            omissions += gap[1];
            insertions += gap[2];
            referencePosition = block.referenceStart() + block.size();
            spokenPosition = block.candidateStart() + block.size();
        }
        int[] tail = gap(reference.size() - referencePosition, spoken.size() - spokenPosition);
        replacements += tail[0];
        omissions += tail[1];
        insertions += tail[2];
        int errors = replacements + omissions + insertions;
        return new ReadAloudContent(reference.size(), Math.max(0, reference.size() - errors), replacements,
                omissions, insertions);
    }

    public Map<String, Object> detail() {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("wordCount", wordCount);
        detail.put("achieved", achieved);
        detail.put("replacements", replacements);
        detail.put("omissions", omissions);
        detail.put("insertions", insertions);
        return detail;
    }

    /** {replacements, omissions, insertions} of one unaligned stretch. */
    private static int[] gap(int referenceWords, int spokenWords) {
        int replaced = Math.min(referenceWords, spokenWords);
        return new int[] {replaced, referenceWords - replaced, spokenWords - replaced};
    }
}
