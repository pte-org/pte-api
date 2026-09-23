package com.pte.scoring;

import com.pte.scoring.dto.response.ExaminerScoringWorkItemView;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExaminerScoringWorkItemViewTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void serializedExaminerContractContainsNoHostOrAiScoreData() {
        ExaminerScoringWorkItemView view = new ExaminerScoringWorkItemView(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "WRITE_ESSAY", "{\"text\":\"candidate response\"}");

        String json = jsonMapper.writeValueAsString(view);

        assertThat(json).contains("answerPublicId", "attemptPublicId", "taskType", "responsePayload")
                .doesNotContain("rawScore", "aiProvider", "teacherScore", "examinerScore", "selectedScoreSource");
    }
}
