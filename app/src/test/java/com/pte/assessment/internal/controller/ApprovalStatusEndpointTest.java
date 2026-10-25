package com.pte.assessment.internal.controller;

import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.request.RejectBlueprintRequest;
import com.pte.assessment.dto.request.RejectExamDefinitionRequest;
import com.pte.assessment.internal.dto.response.BlueprintResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.internal.service.BlueprintService;
import com.pte.assessment.internal.service.ExamDefinitionAdminService;
import com.pte.itembank.TaskTypeRolloutProperties;
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

/** Blueprint and exam-definition review decisions share {@code PATCH .../status}; roles are checked per action. */
class ApprovalStatusEndpointTest {

    private final UUID id = UUID.randomUUID();
    private final CurrentUser author = StatusEndpointHarness.user("ACADEMIC_STAFF");
    private final CurrentUser manager = StatusEndpointHarness.user("ACADEMIC_MANAGER");

    private BlueprintController blueprints(BlueprintService service) {
        return new BlueprintController(service);
    }

    private ExamDefinitionController definitions(ExamDefinitionAdminService service) {
        return new ExamDefinitionController(service, mock(ExamDefinitionService.class), new TaskTypeRolloutProperties());
    }

    @Test
    void anAuthorMaySubmitABlueprintButNotApproveOrReject() throws Exception {
        BlueprintService service = mock(BlueprintService.class);
        when(service.submitApproval(id, author)).thenReturn(mock(BlueprintResponse.class));
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(blueprints(service)).as(author)) {
            harness.patchStatus("/api/v1/blueprints/{id}/status", "{\"action\":\"SUBMIT_APPROVAL\"}", id)
                    .andExpect(status().isOk());
            harness.patchStatus("/api/v1/blueprints/{id}/status", "{\"action\":\"APPROVE\"}", id)
                    .andExpect(status().isForbidden());
            harness.patchStatus("/api/v1/blueprints/{id}/status",
                    "{\"action\":\"REJECT\",\"reason\":\"no\"}", id).andExpect(status().isForbidden());
        }
        verify(service).submitApproval(id, author);
        org.mockito.Mockito.verifyNoMoreInteractions(service);
    }

    @Test
    void aManagerApprovesAndRejectsWithAReason() throws Exception {
        BlueprintService service = mock(BlueprintService.class);
        when(service.reject(any(), any(), any())).thenReturn(mock(BlueprintResponse.class));
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(blueprints(service)).as(manager)) {
            harness.patchStatus("/api/v1/blueprints/{id}/status", "{\"action\":\"APPROVE\"}", id)
                    .andExpect(status().isOk());
            harness.patchStatus("/api/v1/blueprints/{id}/status",
                    "{\"action\":\"REJECT\",\"reason\":\" too short \"}", id).andExpect(status().isOk());
            harness.patchStatus("/api/v1/blueprints/{id}/status", "{\"action\":\"REJECT\"}", id)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }
        verify(service).approve(id, manager);
        verify(service).reject(id, new RejectBlueprintRequest("too short"), manager);
    }

    @Test
    void examDefinitionActivationAndDecisionsNeedAManagerButSubmissionDoesNot() throws Exception {
        ExamDefinitionAdminService service = mock(ExamDefinitionAdminService.class);
        when(service.submitApproval(id, author)).thenReturn(mock(ExamDefinitionResponse.class));
        when(service.reject(any(), any(), any())).thenReturn(mock(ExamDefinitionResponse.class));
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(definitions(service)).as(author)) {
            harness.patchStatus("/api/v1/exam-definitions/{id}/status", "{\"action\":\"SUBMIT_APPROVAL\"}", id)
                    .andExpect(status().isOk());
            for (String action : new String[] {"APPROVE", "ACTIVATE"}) {
                harness.patchStatus("/api/v1/exam-definitions/{id}/status", "{\"action\":\"" + action + "\"}", id)
                        .andExpect(status().isForbidden());
            }
            harness.as(manager).patchStatus("/api/v1/exam-definitions/{id}/status",
                    "{\"action\":\"REJECT\",\"reason\":\"needs work\"}", id).andExpect(status().isOk());
            harness.patchStatus("/api/v1/exam-definitions/{id}/status", "{\"action\":\"REJECT\"}", id)
                    .andExpect(status().isBadRequest());
        }
        verify(service).reject(id, new RejectExamDefinitionRequest("needs work"), manager);
    }

    @Test
    void activatingACustomTemplateIsRefusedWhileTheRolloutFlagIsOff() throws Exception {
        ExamDefinitionAdminService service = mock(ExamDefinitionAdminService.class);
        when(service.isCustomTemplate(id)).thenReturn(true);
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(definitions(service)).as(manager)) {
            harness.patchStatus("/api/v1/exam-definitions/{id}/status", "{\"action\":\"ACTIVATE\"}", id)
                    .andExpect(status().isConflict());
        }
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never()).activate(any(), any());
    }

    @Test
    void activatingACustomTemplateIsAllowedOnceTheRolloutFlagIsOn() throws Exception {
        ExamDefinitionAdminService service = mock(ExamDefinitionAdminService.class);
        when(service.isCustomTemplate(id)).thenReturn(true);
        when(service.activate(id, manager)).thenReturn(mock(ExamDefinitionResponse.class));
        TaskTypeRolloutProperties rollout = new TaskTypeRolloutProperties();
        rollout.setCustomTemplateActivationEnabled(true);
        ExamDefinitionController controller = new ExamDefinitionController(service,
                mock(ExamDefinitionService.class), rollout);
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(controller).as(manager)) {
            harness.patchStatus("/api/v1/exam-definitions/{id}/status", "{\"action\":\"ACTIVATE\"}", id)
                    .andExpect(status().isOk());
        }
        verify(service).activate(id, manager);
    }

    @Test
    void activatingAStandardTemplateDoesNotNeedTheRolloutFlag() throws Exception {
        ExamDefinitionAdminService service = mock(ExamDefinitionAdminService.class);
        when(service.activate(id, manager)).thenReturn(mock(ExamDefinitionResponse.class));
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(definitions(service)).as(manager)) {
            harness.patchStatus("/api/v1/exam-definitions/{id}/status", "{\"action\":\"ACTIVATE\"}", id)
                    .andExpect(status().isOk());
        }
        verify(service).activate(id, manager);
    }
}
