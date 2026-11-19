package com.pte.itembank.internal.generation.controller;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.internal.generation.batch.BatchDraftDeletionService;
import com.pte.itembank.internal.generation.batch.QuestionGenerationBatchQueryService;
import com.pte.itembank.internal.generation.batch.QuestionGenerationBatchService;
import com.pte.itembank.internal.generation.batch.dto.BatchDraftDeleteResponse;
import com.pte.itembank.internal.generation.batch.dto.GenerationBatchChildResponse;
import com.pte.itembank.internal.generation.batch.dto.GenerationBatchResponse;
import com.pte.itembank.internal.generation.domain.BatchRow;
import com.pte.itembank.internal.generation.domain.ImageKind;
import com.pte.itembank.internal.generation.exception.GenerationBatchNotFoundException;
import com.pte.itembank.internal.generation.exception.GenerationDisabledException;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuestionGenerationBatchControllerTest {

    private static final String BASE = "/api/v1/question-generation/batches";
    private static final String BODY = "{\"pool\":\"PRACTICE\",\"rows\":[{\"taskType\":\"READ_ALOUD\",\"cefrLevel\":\"B2\","
            + "\"count\":25,\"topicHints\":[\"climate\"],\"imageKind\":null}]}";

    private final QuestionGenerationBatchService batchService = mock(QuestionGenerationBatchService.class);
    private final QuestionGenerationBatchQueryService queryService = mock(QuestionGenerationBatchQueryService.class);
    private final BatchDraftDeletionService draftDeletion = mock(BatchDraftDeletionService.class);
    private final CurrentUser staff = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));
    private MockedStatic<CurrentUserContext> context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = Mockito.mockStatic(CurrentUserContext.class);
        context.when(CurrentUserContext::required).thenReturn(staff);
        mvc = MockMvcBuilders.standaloneSetup(new QuestionGenerationBatchController(batchService, queryService,
                        draftDeletion))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    private static GenerationBatchResponse batch(UUID id, String status) {
        UUID child = UUID.randomUUID();
        return new GenerationBatchResponse(id, "PRACTICE", status, 25, 0, 0, 0, 25, false, null, child,
                Instant.parse("2026-11-17T10:00:00Z"), null, null,
                List.of(new BatchRow("READ_ALOUD", CefrLevel.B2, 25, List.of("climate"), ImageKind.AUTO)),
                List.of(new GenerationBatchChildResponse(child, 0, 0, "READ_ALOUD", "climate", 10, "QUEUED", 0, 0,
                        null, null, null)));
    }

    @Test
    void submitAnswers202WithTheContractFieldNames() throws Exception {
        UUID id = UUID.randomUUID();
        when(batchService.submit(any(), eq(staff))).thenReturn(batch(id, "QUEUED"));

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.publicId").value(id.toString()))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.totalRequested").value(25))
                .andExpect(jsonPath("$.data.openCount").value(25))
                .andExpect(jsonPath("$.data.cancelRequested").value(false))
                .andExpect(jsonPath("$.data.currentJobPublicId").isNotEmpty())
                .andExpect(jsonPath("$.data.rows[0].taskType").value("READ_ALOUD"))
                .andExpect(jsonPath("$.data.rows[0].cefrLevel").value("B2"))
                .andExpect(jsonPath("$.data.rows[0].topicHints[0]").value("climate"))
                .andExpect(jsonPath("$.data.rows[0].imageKind").value("AUTO"))
                .andExpect(jsonPath("$.data.children[0].jobPublicId").isNotEmpty())
                .andExpect(jsonPath("$.data.children[0].batchSeq").value(0))
                .andExpect(jsonPath("$.data.children[0].rowIndex").value(0))
                .andExpect(jsonPath("$.data.children[0].topicHint").value("climate"))
                .andExpect(jsonPath("$.data.children[0].requestedCount").value(10));
    }

    @Test
    void aBatchWithoutRowsOrWithTooManyIdsIsRejectedAtTheBoundary() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("{\"pool\":\"PRACTICE\",\"rows\":[]}"))
                .andExpect(status().isBadRequest());
        String ids = Collections.nCopies(201, "\"" + UUID.randomUUID() + "\"").stream()
                .collect(Collectors.joining(","));
        mvc.perform(post(BASE + "/{id}/drafts/delete", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionPublicIds\":[" + ids + "]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(batchService, draftDeletion);
    }

    @Test
    void theFlagOffIsServiceUnavailableWithItsCode() throws Exception {
        when(batchService.submit(any(), any())).thenThrow(
                new GenerationDisabledException("GENERATION_BATCH_DISABLED", "off"));

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GENERATION_BATCH_DISABLED"));
    }

    @Test
    void aCallerWithoutAWriterRoleIsForbidden() throws Exception {
        when(batchService.submit(any(), any())).thenThrow(new AccessDeniedException("ACCESS_DENIED"));

        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void anotherWritersBatchIsNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(queryService.get(id, staff)).thenThrow(new GenerationBatchNotFoundException());
        when(batchService.cancel(id, staff)).thenThrow(new GenerationBatchNotFoundException());

        mvc.perform(get(BASE + "/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GENERATION_BATCH_NOT_FOUND"));
        mvc.perform(post(BASE + "/{id}/cancel", id)).andExpect(status().isNotFound());
    }

    @Test
    void listCancelAndBulkDeleteDelegate() throws Exception {
        UUID id = UUID.randomUUID();
        when(queryService.list(0, 20, true, staff)).thenReturn(new PagedResult<>(List.of(batch(id, "RUNNING")),
                new PageMeta(0, 20, 1, 1, true, true, false, false)));
        when(batchService.cancel(id, staff)).thenReturn(batch(id, "CANCELLING"));
        UUID question = UUID.randomUUID();
        when(draftDeletion.deleteDrafts(eq(id), any(), eq(staff))).thenReturn(new BatchDraftDeleteResponse(1, 0, 0,
                List.of(new BatchDraftDeleteResponse.Item(question, "skipped_not_draft",
                        "QUESTION_DELETE_NOT_ALLOWED"))));

        mvc.perform(get(BASE).param("mine", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].publicId").value(id.toString()))
                .andExpect(jsonPath("$.data.meta.totalElements").value(1));
        mvc.perform(post(BASE + "/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLING"));
        mvc.perform(post(BASE + "/{id}/drafts/delete", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processed").value(1))
                .andExpect(jsonPath("$.data.remaining").value(0))
                .andExpect(jsonPath("$.data.items[0].questionPublicId").value(question.toString()))
                .andExpect(jsonPath("$.data.items[0].result").value("skipped_not_draft"))
                .andExpect(jsonPath("$.data.items[0].code").value("QUESTION_DELETE_NOT_ALLOWED"));
    }

    @Test
    void everyRouteIsRestrictedToQuestionWriters() {
        PreAuthorize classLevel = QuestionGenerationBatchController.class.getAnnotation(PreAuthorize.class);

        assertThat(classLevel.value())
                .isEqualTo("hasAnyRole('PLATFORM_ADMIN','ACADEMIC_MANAGER','ACADEMIC_STAFF','PLATFORM_AUTHOR')");
        for (var method : QuestionGenerationBatchController.class.getDeclaredMethods()) {
            assertThat(method.getAnnotation(PreAuthorize.class)).isNull();
        }
    }
}
