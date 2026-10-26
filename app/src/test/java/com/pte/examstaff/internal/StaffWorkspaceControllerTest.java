package com.pte.examstaff.internal;

import com.pte.shared.exception.GlobalExceptionHandler;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** One session list per staff member, chosen with {@code role=PROCTOR|EXAMINER}. */
class StaffWorkspaceControllerTest {

    private final StaffWorkspaceService service = mock(StaffWorkspaceService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new StaffWorkspaceController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    private final CurrentUser host = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));

    @Test
    void theRoleParameterSelectsWhichAssignmentsAreListed() throws Exception {
        UUID staff = UUID.randomUUID();
        try (MockedStatic<CurrentUserContext> context = mockStatic(CurrentUserContext.class)) {
            context.when(CurrentUserContext::required).thenReturn(host);
            mvc.perform(get("/api/v1/exam-staff/{id}/sessions", staff).param("role", "EXAMINER"))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/v1/exam-staff/{id}/sessions", staff).param("role", "PROCTOR"))
                    .andExpect(status().isOk());
        }
        verify(service).examiner(org.mockito.ArgumentMatchers.eq(staff), any(), org.mockito.ArgumentMatchers.eq(host));
        verify(service).proctor(org.mockito.ArgumentMatchers.eq(staff), any(), org.mockito.ArgumentMatchers.eq(host));
        verifyNoMoreInteractions(service);
    }

    @Test
    void aMissingOrUnknownRoleIsABadRequest() throws Exception {
        try (MockedStatic<CurrentUserContext> context = mockStatic(CurrentUserContext.class)) {
            context.when(CurrentUserContext::required).thenReturn(host);
            mvc.perform(get("/api/v1/exam-staff/{id}/sessions", UUID.randomUUID()))
                    .andExpect(status().is4xxClientError());
            mvc.perform(get("/api/v1/exam-staff/{id}/sessions", UUID.randomUUID()).param("role", "STUDENT"))
                    .andExpect(status().is4xxClientError());
        }
    }
}
