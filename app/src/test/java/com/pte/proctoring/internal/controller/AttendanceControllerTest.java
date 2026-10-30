package com.pte.proctoring.internal.controller;

import com.pte.proctoring.internal.service.AttendanceCoordinatorService;
import com.pte.session.dto.response.AttendancePageView;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Hosts and proctors share one attendance route set; the service gets told which of the two is asking. */
class AttendanceControllerTest {

    private final UUID sessionId = UUID.randomUUID();
    private final AttendanceCoordinatorService service = mock(AttendanceCoordinatorService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new AttendanceController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    private void rosterAs(CurrentUser caller, boolean expectedHostFlag) throws Exception {
        when(service.getAttendanceRoster(eq(sessionId), any(), any(), anyInt(), anyInt(), eq(caller),
                eq(expectedHostFlag))).thenReturn(mock(AttendancePageView.class));
        try (MockedStatic<CurrentUserContext> context = mockStatic(CurrentUserContext.class)) {
            context.when(CurrentUserContext::required).thenReturn(caller);
            mvc.perform(get("/api/v1/exam-sessions/{id}/attendance", sessionId)).andExpect(status().isOk());
        }
        verify(service).getAttendanceRoster(eq(sessionId), any(), any(), anyInt(), anyInt(), eq(caller),
                eq(expectedHostFlag));
    }

    @Test
    void aHostAdminReadsTheRosterWithTheHostFlag() throws Exception {
        rosterAs(new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN")), true);
    }

    @Test
    void aProctorReadsTheSameRouteWithoutTheHostFlag() throws Exception {
        rosterAs(new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("PROCTOR")), false);
    }

    @Test
    void thePageSizeIsClampedForEveryCaller() throws Exception {
        CurrentUser host = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));
        when(service.getAttendanceRoster(any(), any(), any(), anyInt(), anyInt(), any(), eq(true)))
                .thenReturn(mock(AttendancePageView.class));
        try (MockedStatic<CurrentUserContext> context = mockStatic(CurrentUserContext.class)) {
            context.when(CurrentUserContext::required).thenReturn(host);
            mvc.perform(get("/api/v1/exam-sessions/{id}/attendance", sessionId).param("size", "5000"))
                    .andExpect(status().isOk());
        }
        verify(service).getAttendanceRoster(eq(sessionId), any(), any(), eq(0), eq(100), eq(host), eq(true));
    }
}
