package com.pte.practice.internal.service.response;

import com.pte.practice.internal.dto.response.PracticeAnswerFeedback;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the student-facing feedback out of a stored AI score detail. Only the fields meant for the student are read
 * (never the transcript alignment numbers or the provider's raw scores); a detail written before this feedback
 * existed, or an unreadable one, gives null and the answer still shows its score.
 */
public final class PracticeFeedbackReader {

    private static final String TRAIT_RATIONALES = "traitRationales";
    private static final String REASON = "reason";
    private static final String TIPS = "tips";
    private static final String WORD_MARKS = "wordMarks";
    private static final String FORM_CHECK = "formCheck";
    private static final String FORM_TRAIT = "FORM";

    private PracticeFeedbackReader() {
    }

    public static PracticeAnswerFeedback read(JsonMapper jsonMapper, String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return null;
        }
        JsonNode detail;
        try {
            detail = jsonMapper.readTree(detailJson);
        } catch (JacksonException ex) {
            return null;
        }
        if (detail == null || !detail.isObject()) {
            return null;
        }
        Map<String, String> reasons = withFormReason(reasons(detail.path(TRAIT_RATIONALES)), detail.path(FORM_CHECK));
        List<String> tips = strings(detail.path(TIPS));
        List<PracticeAnswerFeedback.WordMark> marks = wordMarks(detail.path(WORD_MARKS));
        if (reasons.isEmpty() && tips.isEmpty() && marks.isEmpty()) {
            return null;
        }
        return new PracticeAnswerFeedback(reasons, tips, marks);
    }

    /**
     * Form of a written answer is counted in code, not by the model, so the model never explains it and the report
     * would otherwise give a reason for every criterion but the one that can zero the whole answer. The counts the
     * scorer stored (words, sentences, capitals, punctuation, bullets) are enough to say which rule decided, in the
     * same order the rubric applies them. It goes first, since a Form of 0 outweighs everything after it.
     */
    private static Map<String, String> withFormReason(Map<String, String> reasons, JsonNode formCheck) {
        if (!formCheck.isObject() || reasons.containsKey(FORM_TRAIT) || !formCheck.path("band").isNumber()) {
            return reasons;
        }
        Map<String, String> ordered = new LinkedHashMap<>();
        ordered.put(FORM_TRAIT, formReason(formCheck));
        ordered.putAll(reasons);
        return ordered;
    }

    private static String formReason(JsonNode check) {
        int words = check.path("wordCount").asInt(0);
        int sentences = check.path("sentenceCount").asInt(0);
        boolean singleSentence = check.path("requireSingleSentence").asBoolean(false);
        boolean mechanical = check.path("mechanicalRules").asBoolean(false);
        if (check.path("band").asInt(0) > 0) {
            return "Form is met: " + words + " words" + (singleSentence ? " in one sentence." : ".");
        }
        if (mechanical && check.path("allCaps").asBoolean(false)) {
            return "Form is 0 because the response is written entirely in capital letters. A Form of 0 makes the "
                    + "whole score 0.";
        }
        if (mechanical && !check.path("hasPunctuation").asBoolean(true)) {
            return "Form is 0 because the response has no punctuation. A Form of 0 makes the whole score 0.";
        }
        if (mechanical && check.path("bulletOnly").asBoolean(false)) {
            return "Form is 0 because the response is only bullet points or very short sentences. A Form of 0 makes "
                    + "the whole score 0.";
        }
        if (singleSentence && sentences != 1) {
            return "Form is 0 because the response must be one single sentence, but it has " + sentences
                    + " sentences. A Form of 0 makes the whole score 0.";
        }
        return "Form is 0 because the response has " + words + " words, outside the allowed length. A Form of 0 "
                + "makes the whole score 0.";
    }

    private static Map<String, String> reasons(JsonNode rationales) {
        Map<String, String> reasons = new LinkedHashMap<>();
        if (rationales.isObject()) {
            for (Map.Entry<String, JsonNode> trait : rationales.properties()) {
                String reason = trait.getValue().path(REASON).asText("").strip();
                if (!reason.isEmpty()) {
                    reasons.put(trait.getKey(), reason);
                }
            }
        }
        return reasons;
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode value : array) {
            String text = value.isString() ? value.asText("").strip() : "";
            if (!text.isEmpty()) {
                values.add(text);
            }
        }
        return values;
    }

    private static List<PracticeAnswerFeedback.WordMark> wordMarks(JsonNode array) {
        List<PracticeAnswerFeedback.WordMark> marks = new ArrayList<>();
        for (JsonNode value : array) {
            String word = value.path("word").asText("");
            String mark = value.path("mark").asText("");
            if (!word.isBlank() && !mark.isBlank()) {
                marks.add(new PracticeAnswerFeedback.WordMark(word, mark));
            }
        }
        return marks;
    }
}
