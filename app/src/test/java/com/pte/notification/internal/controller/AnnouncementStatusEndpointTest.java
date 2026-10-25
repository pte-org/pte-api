package com.pte.notification.internal.controller;

import com.pte.notification.internal.dto.request.AnnouncementPublishRequest;
import com.pte.notification.internal.dto.response.AnnouncementResponse;
import com.pte.notification.internal.service.AnnouncementService;
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

/** Publishing and retrying the delivery of an announcement share {@code PATCH /announcements/{id}/status}. */
class AnnouncementStatusEndpointTest {

    @Test
    void publishCarriesTheReviewedDraftVersionAndRetryNeedsNone() throws Exception {
        UUID id = UUID.randomUUID();
        CurrentUser operator = StatusEndpointHarness.user("PLATFORM_ADMIN");
        AnnouncementService service = mock(AnnouncementService.class);
        AnnouncementResponse response = mock(AnnouncementResponse.class);
        when(service.publish(operator, id, new AnnouncementPublishRequest(2L))).thenReturn(response);
        when(service.retryDelivery(operator, id)).thenReturn(response);
        try (StatusEndpointHarness harness = StatusEndpointHarness
                .of(new AnnouncementController(service)).as(operator)) {
            harness.patchStatus("/api/v1/announcements/{id}/status",
                    "{\"action\":\"PUBLISH\",\"expectedDraftVersion\":2}", id).andExpect(status().isOk());
            harness.patchStatus("/api/v1/announcements/{id}/status", "{\"action\":\"RETRY_DELIVERY\"}", id)
                    .andExpect(status().isOk());
        }
        verify(service).publish(operator, id, new AnnouncementPublishRequest(2L));
        verify(service).retryDelivery(operator, id);
    }
}
