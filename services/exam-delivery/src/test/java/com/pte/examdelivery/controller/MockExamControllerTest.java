package com.pte.examdelivery.controller;

import com.pte.examdelivery.dto.request.StartAttemptRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class MockExamControllerTest {

    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = standaloneSetup(new MockExamController(
                "http://localhost:8085/api/exam-delivery/mock-attempts/media")).build();
    }

    @Test
    void startNextAndAudio_returnAppCompatibleResponseAndLocalWav() throws Exception {
        String body = "{\"sessionPublicId\":\"" + SESSION_ID + "\",\"deviceCheckConfirmed\":false}";
        String startResponse = mockMvc.perform(post("/mock-attempts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.completed").value(false))
                .andExpect(jsonPath("$.data.task.totalTasks").value(2))
                .andExpect(jsonPath("$.data.task.taskType").value("REPEAT_SENTENCE"))
                .andExpect(jsonPath("$.data.task.audioPromptRef").value("00000000-0000-0000-0000-000000000101"))
                .andExpect(jsonPath("$.data.task.preListenSeconds").value(3))
                .andExpect(jsonPath("$.data.task.preRecordSeconds").value(3))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String attemptId = com.jayway.jsonpath.JsonPath.read(startResponse, "$.data.attemptPublicId");
        String firstItemId = com.jayway.jsonpath.JsonPath.read(startResponse, "$.data.task.pinnedItemPublicId");

        mockMvc.perform(get("/mock-attempts/{attemptPublicId}/items/{itemPublicId}/audio", attemptId, firstItemId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.audioUrl").value(
                        "http://localhost:8085/api/exam-delivery/mock-attempts/media/repeat-sentence-01.wav"));

        mockMvc.perform(get("/mock-attempts/{attemptPublicId}/next-task", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.task.orderIndex").value(1))
                .andExpect(jsonPath("$.data.task.title").value("Repeat Sentence — Mock Question 2"));

        mockMvc.perform(get("/mock-attempts/media/repeat-sentence-01.wav"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("audio/wav"))
                .andExpect(content().string(containsString("RIFF")));
    }

    @Test
    void sameSession_resumesSameMockAttempt_andSubmitEndsIt() throws Exception {
        String body = "{\"sessionPublicId\":\"" + SESSION_ID + "\",\"deviceCheckConfirmed\":false}";
        String first = mockMvc.perform(post("/mock-attempts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();
        String attemptId = com.jayway.jsonpath.JsonPath.read(first, "$.data.attemptPublicId");

        String resumed = mockMvc.perform(post("/mock-attempts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(
                (String) com.jayway.jsonpath.JsonPath.read(resumed, "$.data.attemptPublicId"))
                .isEqualTo(attemptId);

        mockMvc.perform(post("/mock-attempts/{attemptPublicId}/submit", attemptId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(true))
                .andExpect(jsonPath("$.data.task").doesNotExist());
    }
}
