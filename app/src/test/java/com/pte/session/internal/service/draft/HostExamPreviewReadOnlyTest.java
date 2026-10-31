package com.pte.session.internal.service.draft;

import com.pte.session.internal.controller.SessionController;
import com.pte.session.internal.dto.response.ExamPreviewResponse;
import com.pte.session.internal.service.SessionLifecycleService;
import com.pte.session.internal.service.audience.ExamAudienceService;
import com.pte.session.internal.service.draft.ExamDraftService;
import com.pte.session.internal.service.draft.ExamPublishService;
import com.pte.shared.exception.GlobalExceptionHandler;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HostExamPreviewReadOnlyTest {

    @Mock
    private SessionLifecycleService sessionLifecycleService;
    @Mock
    private ExamDraftService examDraftService;
    @Mock
    private ExamAudienceService examAudienceService;
    @Mock
    private ExamPublishService examPublishService;
    @Mock
    private SessionExamPreviewService previewService;

    private MockedStatic<CurrentUserContext> currentUserContext;
    private MockMvc mvc;
    private CurrentUser host;

    @BeforeEach
    void setUp() {
        host = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));
        currentUserContext = org.mockito.Mockito.mockStatic(CurrentUserContext.class);
        currentUserContext.when(CurrentUserContext::required).thenReturn(host);
        mvc = MockMvcBuilders.standaloneSetup(new SessionController(sessionLifecycleService, examDraftService,
                        examAudienceService, examPublishService, previewService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        currentUserContext.close();
    }

    @Test
    void previewSerializesStableRuntimeAndTimingWithoutAnswerKeysOrWriteCalls() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        ExamPreviewResponse response = new ExamPreviewResponse(snapshotId, "Preview", 1, List.of(
                new ExamPreviewResponse.Item(
                        0, itemId, "READING", "MC_READING_SINGLE", "MC_READING_SINGLE",
                        "Multiple choice", "Question", "Prompt", null, null, null, null,
                        List.of(new ExamPreviewResponse.Option(0, null, "Option A")), null,
                        "MC_READING_SINGLE", null, "LEGACY", "RESOLVED_LEGACY", 7, 42,
                        null, null, null, null, null, "Select the correct response.")));
        when(previewService.preview(sessionId, host)).thenReturn(response);

        mvc.perform(get("/api/v1/sessions/{publicId}/exam-preview", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshotPublicId").value(snapshotId.toString()))
                .andExpect(jsonPath("$.data.items[0].snapshotItemPublicId").value(itemId.toString()))
                .andExpect(jsonPath("$.data.items[0].prepSeconds").value(7))
                .andExpect(jsonPath("$.data.items[0].responseSeconds").value(42))
                .andExpect(jsonPath("$.data.items[0].options[0].text").value("Option A"))
                .andExpect(jsonPath("$.data.items[0].taskTypeInstruction").value("Select the correct response."))
                .andExpect(content().string(not(containsString("correctAnswerText"))))
                .andExpect(content().string(not(containsString("referenceAnswerText"))))
                .andExpect(content().string(not(containsString("isCorrect"))));

        verify(previewService).preview(sessionId, host);
        verify(sessionLifecycleService, never()).open(sessionId, host);
        verify(examPublishService, never()).publish(sessionId, host);
    }
}
