package com.pte.enrollment.internal.controller;

import com.pte.enrollment.internal.dto.response.ClassResponse;
import com.pte.enrollment.internal.dto.response.ProgramResponse;
import com.pte.enrollment.internal.service.ClassLifecycleService;
import com.pte.enrollment.internal.service.ClassReorganizationService;
import com.pte.enrollment.internal.service.ClassService;
import com.pte.enrollment.internal.service.ClassStudentService;
import com.pte.enrollment.internal.service.ProgramService;
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

/** One {@code PATCH .../status} route replaces the four lifecycle POSTs of a program and of a class. */
class TrainingStatusEndpointTest {

    private final UUID organization = UUID.randomUUID();
    private final UUID program = UUID.randomUUID();
    private final UUID klass = UUID.randomUUID();
    private final CurrentUser host = StatusEndpointHarness.user("HOST_ADMIN");

    @Test
    void everyProgramActionReachesItsLifecycleMethod() throws Exception {
        ProgramService service = mock(ProgramService.class);
        ProgramResponse response = mock(ProgramResponse.class);
        when(service.activate(organization, program, host)).thenReturn(response);
        when(service.deactivate(organization, program, host)).thenReturn(response);
        when(service.suspend(organization, program, host)).thenReturn(response);
        when(service.archive(organization, program, host)).thenReturn(response);
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(new ProgramController(service)).as(host)) {
            for (String action : new String[] {"ACTIVATE", "DEACTIVATE", "SUSPEND", "ARCHIVE"}) {
                harness.patchStatus("/api/v1/organizations/{o}/programs/{p}/status",
                        "{\"action\":\"" + action + "\"}", organization, program)
                        .andExpect(status().isOk());
            }
        }
        verify(service).activate(organization, program, host);
        verify(service).deactivate(organization, program, host);
        verify(service).suspend(organization, program, host);
        verify(service).archive(organization, program, host);
    }

    @Test
    void everyClassActionReachesItsLifecycleMethod() throws Exception {
        ClassLifecycleService lifecycle = mock(ClassLifecycleService.class);
        ClassResponse response = mock(ClassResponse.class);
        when(lifecycle.activate(organization, program, klass, host)).thenReturn(response);
        when(lifecycle.deactivate(organization, program, klass, host)).thenReturn(response);
        when(lifecycle.suspend(organization, program, klass, host)).thenReturn(response);
        when(lifecycle.archive(organization, program, klass, host)).thenReturn(response);
        ClassController controller = new ClassController(mock(ClassService.class), lifecycle,
                mock(ClassStudentService.class), mock(ClassReorganizationService.class));
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(controller).as(host)) {
            for (String action : new String[] {"ACTIVATE", "DEACTIVATE", "SUSPEND", "ARCHIVE"}) {
                harness.patchStatus("/api/v1/organizations/{o}/programs/{p}/classes/{c}/status",
                        "{\"action\":\"" + action + "\"}", organization, program, klass)
                        .andExpect(status().isOk());
            }
        }
        verify(lifecycle).activate(organization, program, klass, host);
        verify(lifecycle).archive(organization, program, klass, host);
    }

    @Test
    void anUnknownOrMissingActionIsRejectedBeforeTheService() throws Exception {
        ProgramService service = mock(ProgramService.class);
        try (StatusEndpointHarness harness = StatusEndpointHarness.of(new ProgramController(service)).as(host)) {
            harness.patchStatus("/api/v1/organizations/{o}/programs/{p}/status", "{\"action\":\"DELETE\"}",
                    organization, program).andExpect(status().isBadRequest());
            harness.patchStatus("/api/v1/organizations/{o}/programs/{p}/status", "{}", organization, program)
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
