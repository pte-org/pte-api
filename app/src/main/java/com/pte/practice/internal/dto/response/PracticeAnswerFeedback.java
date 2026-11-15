package com.pte.practice.internal.dto.response;

import java.util.List;
import java.util.Map;

/**
 * The feedback that came with an AI score, beyond its summary text: why each trait got its level, what to do next,
 * and for a read-out task the words the student left out or added.
 *
 * @param traitReasons trait key -> one-sentence reason (Vietnamese for answers the model heard itself)
 * @param tips         one or two concrete actions that would raise the score
 * @param wordMarks    for Read Aloud and Repeat Sentence: omitted reference words and inserted words
 */
public record PracticeAnswerFeedback(Map<String, String> traitReasons, List<String> tips, List<WordMark> wordMarks) {

    public PracticeAnswerFeedback {
        traitReasons = traitReasons == null ? Map.of() : Map.copyOf(traitReasons);
        tips = tips == null ? List.of() : List.copyOf(tips);
        wordMarks = wordMarks == null ? List.of() : List.copyOf(wordMarks);
    }

    /** {@code mark} is OMITTED or INSERTED. */
    public record WordMark(String word, String mark) {
    }
}
