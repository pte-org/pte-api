package com.pte.practice.internal.controller;

import com.pte.practice.internal.dto.response.PracticeReviewResponse;
import com.pte.practice.internal.dto.response.PracticeTaskContentResponse;
import com.pte.practice.internal.service.PracticeReviewService;
import com.pte.practice.internal.service.session.PracticeSessionService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The review URL and response shape survive the removal of the explanation controller it used to live in. */
class PracticeSessionControllerReviewTest {

    private final PracticeReviewService reviewService = mock(PracticeReviewService.class);
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("STUDENT"));
    private MockedStatic<CurrentUserContext> context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = mockStatic(CurrentUserContext.class);
        context.when(CurrentUserContext::required).thenReturn(caller);
        mvc = MockMvcBuilders.standaloneSetup(
                new PracticeSessionController(mock(PracticeSessionService.class), reviewService)).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void reviewReturnsTheSessionItemsWithTheirScores() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Instant scoredAt = Instant.parse("2026-10-24T01:00:00Z");
        when(reviewService.get(sessionId, caller)).thenReturn(new PracticeReviewResponse(sessionId, List.of(
                new PracticeReviewResponse.Item(itemId, 0, "WRITE_ESSAY", "Write Essay", "WRITING", "ANSWERED",
                        "Prompt", "{\"text\":\"x\"}", "WRITE_ESSAY_V1", null, null, 72, "SCORED",
                        Map.of("content", 70), "GEMINI", "model", scoredAt,
                        List.of(new PracticeTaskContentResponse.Option(null, "First", 0, null))))));

        mvc.perform(get("/api/v1/student/practice/sessions/{id}/review", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionPublicId").value(sessionId.toString()))
                .andExpect(jsonPath("$.data.items[0].publicId").value(itemId.toString()))
                .andExpect(jsonPath("$.data.items[0].aiScore").value(72))
                .andExpect(jsonPath("$.data.items[0].aiScoreStatus").value("SCORED"))
                .andExpect(jsonPath("$.data.items[0].aiSubScores.content").value(70))
                .andExpect(jsonPath("$.data.items[0].options[0].text").value("First"))
                .andExpect(jsonPath("$.data.items[0].options[0].orderIndex").value(0));
    }
}
