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
        Map<String, String> reasons = reasons(detail.path(TRAIT_RATIONALES));
        List<String> tips = strings(detail.path(TIPS));
        List<PracticeAnswerFeedback.WordMark> marks = wordMarks(detail.path(WORD_MARKS));
        if (reasons.isEmpty() && tips.isEmpty() && marks.isEmpty()) {
            return null;
        }
        return new PracticeAnswerFeedback(reasons, tips, marks);
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
