package com.pte.itembank.internal.generation.controller;

import com.pte.itembank.internal.generation.dto.GenerationJobResponse;
import com.pte.itembank.internal.generation.dto.GenerationQuotaResponse;
import com.pte.itembank.internal.generation.dto.GenerationTaskTypeResponse;
import com.pte.itembank.internal.generation.exception.GenerationLimitException;
import com.pte.itembank.internal.generation.job.QuestionGenerationJobService;
import com.pte.itembank.internal.generation.job.QuestionGenerationQueryService;
import com.pte.shared.exception.GlobalExceptionHandler;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuestionGenerationControllerTest {

    private static final String BODY =
            "{\"taskType\":\"MC_READING_SINGLE\",\"cefrLevel\":\"B2\",\"pool\":\"PRACTICE\",\"count\":3}";

    private final QuestionGenerationJobService jobService = mock(QuestionGenerationJobService.class);
    private final QuestionGenerationQueryService queryService = mock(QuestionGenerationQueryService.class);
    private final CurrentUser admin = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
    private MockedStatic<CurrentUserContext> context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = Mockito.mockStatic(CurrentUserContext.class);
        context.when(CurrentUserContext::required).thenReturn(admin);
        mvc = MockMvcBuilders.standaloneSetup(new QuestionGenerationController(jobService, queryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    private static GenerationJobResponse job(UUID id, String status) {
        return new GenerationJobResponse(id, "MC_READING_SINGLE", "B2", "PRACTICE", null, 3, status, 0, 0, null,
                Instant.parse("2026-11-07T10:00:00Z"), null, null, List.of());
    }

    @Test
    void submitAnswers202WithTheEnvelopeAndTheJob() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.submit(any(), eq(admin))).thenReturn(job(id, "QUEUED"));

        mvc.perform(post("/api/v1/question-generation/jobs").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.publicId").value(id.toString()))
                .andExpect(jsonPath("$.data.status").value("QUEUED"));
    }

    @Test
    void anOutOfRangeCountIsRejectedAtTheBoundary() throws Exception {
        String body = "{\"taskType\":\"MC_READING_SINGLE\",\"cefrLevel\":\"B2\",\"pool\":\"PRACTICE\",\"count\":11}";

        mvc.perform(post("/api/v1/question-generation/jobs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(jobService);
    }

    @Test
    void aMissingTaskTypeIsRejectedAtTheBoundary() throws Exception {
        String body = "{\"cefrLevel\":\"B2\",\"pool\":\"PRACTICE\",\"count\":1}";

        mvc.perform(post("/api/v1/question-generation/jobs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(jobService);
    }

    @Test
    void aLimitIsMappedToTooManyRequestsWithItsCode() throws Exception {
        when(jobService.submit(any(), any())).thenThrow(
                new GenerationLimitException("GENERATION_DAILY_LIMIT_REACHED", "You reached the daily limit."));

        mvc.perform(post("/api/v1/question-generation/jobs").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("GENERATION_DAILY_LIMIT_REACHED"));
    }

    @Test
    void getAndListReturnTheServiceResults() throws Exception {
        UUID id = UUID.randomUUID();
        when(queryService.get(id, admin)).thenReturn(job(id, "RUNNING"));
        when(queryService.list(0, 20, admin)).thenReturn(new PagedResult<>(List.of(job(id, "RUNNING")),
                new PageMeta(0, 20, 1, 1, true, true, false, false)));

        mvc.perform(get("/api/v1/question-generation/jobs/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RUNNING"));
        mvc.perform(get("/api/v1/question-generation/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].publicId").value(id.toString()))
                .andExpect(jsonPath("$.data.meta.totalElements").value(1));
    }

    @Test
    void cancelGoesThroughThePatchStatusRoute() throws Exception {
        UUID id = UUID.randomUUID();
        when(jobService.cancel(id, admin)).thenReturn(job(id, "CANCELLING"));

        mvc.perform(patch("/api/v1/question-generation/jobs/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"CANCEL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLING"));
        mvc.perform(patch("/api/v1/question-generation/jobs/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"EXPLODE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void taskTypesAndQuotaAreExposedForTheForm() throws Exception {
        when(queryService.taskTypes(admin)).thenReturn(
                List.of(new GenerationTaskTypeResponse("MC_READING_SINGLE", "READING", false, false, 45)));
        when(queryService.quota(admin)).thenReturn(new GenerationQuotaResponse(12, 100));

        mvc.perform(get("/api/v1/question-generation/task-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].taskType").value("MC_READING_SINGLE"))
                .andExpect(jsonPath("$.data[0].estimatedSecondsPerItem").value(45));
        mvc.perform(get("/api/v1/question-generation/quota"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedToday").value(12))
                .andExpect(jsonPath("$.data.dailyLimit").value(100));
    }

    @Test
    void everyRouteIsRestrictedToPlatformAdminsAndNoMethodBroadensIt() {
        PreAuthorize classLevel = QuestionGenerationController.class.getAnnotation(PreAuthorize.class);

        assertThat(classLevel).isNotNull();
        assertThat(classLevel.value()).isEqualTo("hasRole('PLATFORM_ADMIN')");
        for (var method : QuestionGenerationController.class.getDeclaredMethods()) {
            assertThat(method.getAnnotation(PreAuthorize.class))
                    .as("method %s must not override the class-level restriction", method.getName()).isNull();
        }
    }
}
