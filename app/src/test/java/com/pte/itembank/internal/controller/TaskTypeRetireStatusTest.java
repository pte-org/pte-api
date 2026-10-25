package com.pte.itembank.internal.controller;

import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.TaskTypeRolloutProperties;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.StatusEndpointHarness;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Retiring a task type is a status action now, open to the same authors as before. */
class TaskTypeRetireStatusTest {

    @Test
    void anAuthorCanRetireATaskTypeThroughTheStatusRoute() throws Exception {
        UUID id = UUID.randomUUID();
        CurrentUser author = StatusEndpointHarness.user("ACADEMIC_STAFF");
        QuestionTypeService service = mock(QuestionTypeService.class);
        TaskTypeController controller = new TaskTypeController(service, new TaskTypeRolloutProperties());
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(controller).as(author)) {
            harness.patchStatus("/api/v1/task-types/{id}/status", "{\"action\":\"RETIRE\"}", id)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
        verify(service).retireTaskType(id, author);
    }
}
