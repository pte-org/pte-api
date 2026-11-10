package com.pte.scoring.internal.service.examiner.trait;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExaminerAiGapReasonsTest {

    private final ExaminerTraitResponses responses = new ExaminerTraitResponses(JsonMapper.builder().build());

    @Test
    void keepsOnlyNonBlankReasonsOfScoredTraitsAndReadsThemBack() {
        Map<String, String> reasons = new HashMap<>();
        reasons.put("CONTENT", "  Off topic  ");
        reasons.put("GRAMMAR", " ");
        reasons.put("UNKNOWN", "not a scored trait");

        String stored = responses.aiGapReasonsJson(reasons, Map.of("CONTENT", 2, "GRAMMAR", 1));

        assertThat(responses.aiGapReasons(stored)).containsExactlyEntriesOf(Map.of("CONTENT", "Off topic"));
    }

    @Test
    void noRemainingReasonStoresNullAndUnreadableValuesReadAsEmpty() {
        assertThat(responses.aiGapReasonsJson(Map.of("CONTENT", ""), Map.of("CONTENT", 1))).isNull();
        assertThat(responses.aiGapReasonsJson(null, Map.of("CONTENT", 1))).isNull();
        assertThat(responses.aiGapReasons("not json")).isEmpty();
        assertThat(responses.aiGapReasons(null)).isEmpty();
    }
}
