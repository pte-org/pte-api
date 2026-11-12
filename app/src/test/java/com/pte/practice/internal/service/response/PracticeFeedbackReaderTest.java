package com.pte.practice.internal.service.response;

import com.pte.practice.internal.dto.response.PracticeAnswerFeedback;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** The student sees the reasons, tips and word marks of a stored score detail, and nothing else from it. */
class PracticeFeedbackReaderTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void readsTheReasonsTipsAndWordMarks() {
        PracticeAnswerFeedback feedback = PracticeFeedbackReader.read(JSON, """
                {"scoreLabel":"UNVALIDATED","speech":{"accuracy":91.0},
                 "traitRationales":{"PRONUNCIATION":{"reason":"Rõ ràng.","evidence":"solar"},
                                    "ORAL_FLUENCY":{"reason":" ","evidence":""}},
                 "tips":["Đọc theo cụm từ.",""],
                 "wordMarks":[{"word":"of","mark":"OMITTED"},{"word":"very","mark":"INSERTED"},{"word":""}]}""");

        assertThat(feedback.traitReasons()).containsExactly(java.util.Map.entry("PRONUNCIATION", "Rõ ràng."));
        assertThat(feedback.tips()).containsExactly("Đọc theo cụm từ.");
        assertThat(feedback.wordMarks()).containsExactly(new PracticeAnswerFeedback.WordMark("of", "OMITTED"),
                new PracticeAnswerFeedback.WordMark("very", "INSERTED"));
    }

    @Test
    void anOlderOrUnreadableDetailHasNoFeedback() {
        assertThat(PracticeFeedbackReader.read(JSON, null)).isNull();
        assertThat(PracticeFeedbackReader.read(JSON, "{\"scoreLabel\":\"UNVALIDATED\"}")).isNull();
        assertThat(PracticeFeedbackReader.read(JSON, "not json")).isNull();
        assertThat(PracticeFeedbackReader.read(JSON, "[1,2]")).isNull();
    }
}
