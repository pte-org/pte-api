package com.pte.scoring.internal.vendor.audioscoring;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.speech.ReadAloudContent;
import com.pte.scoring.internal.rubric.speech.RepeatSentenceContent;
import com.pte.scoring.internal.vendor.audioscoring.AudioTask.ContentRule;
import com.pte.scoring.internal.vendor.speech.AnswerKey;
import com.pte.scoring.internal.vendor.speech.TokenAlignment;
import com.pte.scoring.rubric.RubricTraits;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The traits code counts from the model's transcript, the same rules as the exam's Azure scorers, so a recording
 * scores the same content whoever heard it. Scripted tasks also get their word marks: the passage words the student
 * left out and the words they added, from the word alignment.
 *
 * @param scores    the counted traits
 * @param wordCount Read Aloud's passage length N (its per-word maximum), otherwise null
 * @param detail    what the counting found, kept in the stored detail
 */
record CountedTraits(Map<String, Integer> scores, Integer wordCount, Map<String, Object> detail) {

    static final String WORD_MARKS = "wordMarks";
    static final String MARK_OMITTED = "OMITTED";
    static final String MARK_INSERTED = "INSERTED";
    private static final int APPROPRIATE = 1;

    static CountedTraits count(ContentRule rule, String reference, String transcript) {
        return switch (rule) {
            case MODEL -> new CountedTraits(Map.of(), null, Map.of());
            case READ_ALOUD -> readAloud(reference, transcript);
            case REPEAT_SENTENCE -> repeatSentence(reference, transcript);
            case SHORT_ANSWER -> shortAnswer(reference, transcript);
        };
    }

    private static CountedTraits readAloud(String passage, String transcript) {
        ReadAloudContent content = ReadAloudContent.measure(passage, transcript);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("readAloudContent", content.detail());
        detail.put(WORD_MARKS, wordMarks(passage, transcript));
        return new CountedTraits(Map.of(RubricTraits.CONTENT, content.achieved()), content.wordCount(), detail);
    }

    private static CountedTraits repeatSentence(String sentence, String transcript) {
        RepeatSentenceContent content = RepeatSentenceContent.measure(sentence, transcript);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("repeatSentenceContent", content.detail());
        detail.put(WORD_MARKS, wordMarks(sentence, transcript));
        return new CountedTraits(Map.of(RubricTraits.CONTENT, content.band()), null, detail);
    }

    private static CountedTraits shortAnswer(String answerKey, String transcript) {
        Optional<String> matched = AnswerKey.parse(answerKey).match(transcript);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("matchedAnswer", matched.orElse(null));
        return new CountedTraits(Map.of(RubricTraits.VOCABULARY, matched.isPresent() ? APPROPRIATE : 0), null,
                detail);
    }

    /** Omitted reference words, then inserted words (filler sounds are not words), in the order they occur. */
    static List<Map<String, String>> wordMarks(String reference, String transcript) {
        TokenAlignment alignment = TokenAlignment.align(reference, transcript);
        List<Map<String, String>> marks = new ArrayList<>();
        alignment.missingTokens().forEach(word -> marks.add(mark(word, MARK_OMITTED)));
        alignment.extraTokens().stream().filter(word -> !ScoringConstants.SPEECH_FILLER_WORDS.contains(word))
                .forEach(word -> marks.add(mark(word, MARK_INSERTED)));
        return marks;
    }

    private static Map<String, String> mark(String word, String mark) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("word", word);
        entry.put("mark", mark);
        return entry;
    }
}
