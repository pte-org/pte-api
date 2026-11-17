package com.pte.scoring.internal.vendor.speech;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The accepted answers of a short-question item, authored as
 * {@code canonical / alias / alias}. An answer matches only when its whole
 * normalized word sequence equals the spoken one.
 */
public final class AnswerKey {

    private static final Set<String> LEADING_ARTICLES = Set.of("a", "an", "the");

    private final List<String> acceptedAnswers;
    private final List<List<String>> acceptedTokens;

    private AnswerKey(List<String> acceptedAnswers, List<List<String>> acceptedTokens) {
        this.acceptedAnswers = acceptedAnswers;
        this.acceptedTokens = acceptedTokens;
    }

    public static AnswerKey parse(String text) {
        List<String> answers = new ArrayList<>();
        List<List<String>> tokens = new ArrayList<>();
        Set<List<String>> seen = new HashSet<>();
        if (text != null) {
            for (String part : text.split("/")) {
                List<String> normalized = matchTokens(part);
                if (!normalized.isEmpty() && seen.add(normalized)) {
                    answers.add(part.trim());
                    tokens.add(normalized);
                }
            }
        }
        return new AnswerKey(List.copyOf(answers), List.copyOf(tokens));
    }

    /**
     * Words compared for a match. A leading article is dropped on both sides, so "a thermometer" matches a key of
     * "thermometer" (and the reverse); it is kept when it is the only word, and articles inside the answer stay.
     */
    private static List<String> matchTokens(String text) {
        List<String> tokens = TokenAlignment.normalize(text);
        if (tokens.size() > 1 && LEADING_ARTICLES.contains(tokens.get(0))) {
            return List.copyOf(tokens.subList(1, tokens.size()));
        }
        return tokens;
    }

    /** Canonical answer first, then the aliases. */
    public List<String> acceptedAnswers() {
        return acceptedAnswers;
    }

    /** The accepted answer the transcript matches, as written in the key. */
    public Optional<String> match(String transcript) {
        List<String> spoken = matchTokens(transcript);
        for (int i = 0; i < acceptedTokens.size(); i++) {
            if (acceptedTokens.get(i).equals(spoken)) {
                return Optional.of(acceptedAnswers.get(i));
            }
        }
        return Optional.empty();
    }
}
