package com.pte.session.internal.controller;

import com.pte.session.internal.service.ExamOrchestrationService;
import com.pte.session.internal.service.SessionExamPreviewService;
import com.pte.session.internal.service.SessionLifecycleService;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.exception.GlobalExceptionHandler;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HTTP mapping only: the removed PRACTICE exam mode must be a client error, never a 500. */
class SessionControllerExamModeContractTest {
    private final ExamOrchestrationService orchestration = mock(ExamOrchestrationService.class);
    private final CurrentUser caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));
    private MockedStatic<CurrentUserContext> context;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        context = mockStatic(CurrentUserContext.class);
        context.when(CurrentUserContext::required).thenReturn(caller);
        mvc = MockMvcBuilders.standaloneSetup(new SessionController(mock(SessionLifecycleService.class),
                        orchestration, mock(SessionExamPreviewService.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach void tearDown() { context.close(); }

    @Test void createDraftWithPracticeModeReturns400MalformedRequest() throws Exception {
        Instant opensAt = Instant.now().plusSeconds(3600);
        mvc.perform(post("/api/v1/sessions/drafts").contentType("application/json")
                        .content("{\"name\":\"Mock\",\"templatePublicId\":\"" + UUID.randomUUID()
                                + "\",\"subscriptionPublicId\":\"" + UUID.randomUUID()
                                + "\",\"opensAt\":\"" + opensAt + "\",\"closesAt\":\"" + opensAt.plusSeconds(3600)
                                + "\",\"examMode\":\"PRACTICE\",\"capacity\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(SharedConstants.MALFORMED_REQUEST));
        verify(orchestration, never()).createDraft(any(), any());
    }

    @Test void patchDraftToPracticeModeReturns400MalformedRequest() throws Exception {
        mvc.perform(patch("/api/v1/sessions/{publicId}", UUID.randomUUID()).contentType("application/json")
                        .content("{\"examMode\":\"PRACTICE\",\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(SharedConstants.MALFORMED_REQUEST));
        verify(orchestration, never()).updateDraft(any(), any(), any());
    }
}
