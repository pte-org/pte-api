package com.pte.session.internal.controller;

import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.service.audience.ExamAudienceService;
import com.pte.session.internal.service.draft.ExamDraftService;
import com.pte.session.internal.service.draft.ExamPublishService;
import com.pte.session.internal.service.draft.SessionExamPreviewService;
import com.pte.session.internal.service.SessionLifecycleService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.StatusEndpointHarness;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Publish, cancel, open and close of an exam session share {@code PATCH /sessions/{id}/status}. */
class SessionStatusEndpointTest {

    @Test
    void eachActionReachesTheRightLifecycleService() throws Exception {
        UUID id = UUID.randomUUID();
        CurrentUser host = StatusEndpointHarness.user("HOST_ADMIN");
        SessionLifecycleService lifecycle = mock(SessionLifecycleService.class);
        ExamPublishService publish = mock(ExamPublishService.class);
        SessionResponse response = mock(SessionResponse.class);
        when(publish.publish(id, host)).thenReturn(response);
        when(publish.cancel(id, host)).thenReturn(response);
        when(lifecycle.open(id, host)).thenReturn(response);
        when(lifecycle.close(id, host)).thenReturn(response);
        SessionController controller = new SessionController(lifecycle, mock(ExamDraftService.class),
                mock(ExamAudienceService.class), publish, mock(SessionExamPreviewService.class));
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(controller).as(host)) {
            for (String action : new String[] {"PUBLISH", "CANCEL", "OPEN", "CLOSE"}) {
                harness.patchStatus("/api/v1/sessions/{id}/status", "{\"action\":\"" + action + "\"}", id)
                        .andExpect(status().isOk());
            }
            harness.patchStatus("/api/v1/sessions/{id}/status", "{\"action\":\"DELETE\"}", id)
                    .andExpect(status().isBadRequest());
        }
        verify(publish).publish(id, host);
        verify(publish).cancel(id, host);
        verify(lifecycle).open(id, host);
        verify(lifecycle).close(id, host);
    }
}
