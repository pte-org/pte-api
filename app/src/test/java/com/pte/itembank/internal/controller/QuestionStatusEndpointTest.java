package com.pte.itembank.internal.controller;

import com.pte.itembank.ItembankService;
import com.pte.itembank.dto.request.RejectQuestionRequest;
import com.pte.itembank.dto.response.QuestionResponse;
import com.pte.itembank.internal.service.QuestionDeletionService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.StatusEndpointHarness;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Review, publication and archive of a question all go through {@code PATCH /questions/{id}/status}. */
class QuestionStatusEndpointTest {

    private final UUID id = UUID.randomUUID();
    private final CurrentUser author = StatusEndpointHarness.user("ACADEMIC_STAFF");
    private final CurrentUser manager = StatusEndpointHarness.user("PLATFORM_ADMIN");
    private final ItembankService service = mock(ItembankService.class);
    private final QuestionController controller = new QuestionController(service, mock(QuestionDeletionService.class));

    @Test
    void authorActionsMapToTheirServiceCalls() throws Exception {
        QuestionResponse response = mock(QuestionResponse.class);
        when(service.submitApproval(id, author)).thenReturn(response);
        when(service.archive(id, author)).thenReturn(response);
        when(service.unarchive(id, author)).thenReturn(response);
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(controller).as(author)) {
            for (String action : new String[] {"SUBMIT_APPROVAL", "ARCHIVE", "UNARCHIVE"}) {
                harness.patchStatus("/api/v1/questions/{id}/status", "{\"action\":\"" + action + "\"}", id)
                        .andExpect(status().isOk());
            }
        }
        verify(service).submitApproval(id, author);
        verify(service).archive(id, author);
        verify(service).unarchive(id, author);
    }

    @Test
    void approvePublishAndRejectAreForManagersOnly() throws Exception {
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(controller).as(author)) {
            for (String body : new String[] {"{\"action\":\"APPROVE\"}", "{\"action\":\"PUBLISH\"}",
                    "{\"action\":\"REJECT\",\"reason\":\"x\"}"}) {
                harness.patchStatus("/api/v1/questions/{id}/status", body, id).andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(service);
    }

    @Test
    void aManagerDecidesAndRejectNeedsAReason() throws Exception {
        QuestionResponse response = mock(QuestionResponse.class);
        when(service.approve(id, manager)).thenReturn(response);
        when(service.publish(id, manager)).thenReturn(response);
        when(service.reject(any(), any(), any())).thenReturn(response);
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(controller).as(manager)) {
            harness.patchStatus("/api/v1/questions/{id}/status", "{\"action\":\"APPROVE\"}", id)
                    .andExpect(status().isOk());
            harness.patchStatus("/api/v1/questions/{id}/status", "{\"action\":\"PUBLISH\"}", id)
                    .andExpect(status().isOk());
            harness.patchStatus("/api/v1/questions/{id}/status", "{\"action\":\"REJECT\",\"reason\":\"  \"}", id)
                    .andExpect(status().isBadRequest());
            harness.patchStatus("/api/v1/questions/{id}/status", "{\"action\":\"REJECT\",\"reason\":\"bad\"}", id)
                    .andExpect(status().isOk());
        }
        verify(service).reject(id, new RejectQuestionRequest("bad"), manager);
    }
}
