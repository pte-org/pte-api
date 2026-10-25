package com.pte.identity.internal.controller;

import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.service.ExamStaffQueryService;
import com.pte.identity.internal.service.PlatformUserLifecycleService;
import com.pte.identity.internal.service.PlatformUserService;
import com.pte.identity.internal.service.UserService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.StatusEndpointHarness;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Account lifecycle of tenant users and platform users goes through {@code PATCH .../status}. */
class UserStatusEndpointTest {

    private final UUID id = UUID.randomUUID();
    private final CurrentUser admin = StatusEndpointHarness.user("PLATFORM_ADMIN");

    @Test
    void platformUserActionsReachSuspendReactivateArchiveAndRestore() throws Exception {
        PlatformUserService users = mock(PlatformUserService.class);
        PlatformUserLifecycleService lifecycle = mock(PlatformUserLifecycleService.class);
        UserResponse response = mock(UserResponse.class);
        when(users.suspend(id, admin)).thenReturn(response);
        when(users.reactivate(id, admin)).thenReturn(response);
        when(lifecycle.archive(id, admin)).thenReturn(response);
        when(lifecycle.restore(id, admin)).thenReturn(response);
        try (StatusEndpointHarness harness = StatusEndpointHarness
                .of(new PlatformUserController(users, lifecycle)).as(admin)) {
            for (String action : new String[] {"SUSPEND", "REACTIVATE", "ARCHIVE", "RESTORE"}) {
                harness.patchStatus("/api/v1/platform-users/{id}/status", "{\"action\":\"" + action + "\"}", id)
                        .andExpect(status().isOk());
            }
        }
        verify(users).suspend(id, admin);
        verify(users).reactivate(id, admin);
        verify(lifecycle).archive(id, admin);
        verify(lifecycle).restore(id, admin);
    }

    @Test
    void tenantUsersCanBeSuspendedAndReactivatedButNotArchived() throws Exception {
        UserService users = mock(UserService.class);
        UserResponse response = mock(UserResponse.class);
        when(users.suspend(id, admin)).thenReturn(response);
        when(users.reactivate(id, admin)).thenReturn(response);
        try (StatusEndpointHarness harness = StatusEndpointHarness
                .of(new UserController(users, mock(ExamStaffQueryService.class))).as(admin)) {
            harness.patchStatus("/api/v1/users/{id}/status", "{\"action\":\"SUSPEND\"}", id)
                    .andExpect(status().isOk());
            harness.patchStatus("/api/v1/users/{id}/status", "{\"action\":\"REACTIVATE\"}", id)
                    .andExpect(status().isOk());
            harness.patchStatus("/api/v1/users/{id}/status", "{\"action\":\"ARCHIVE\"}", id)
                    .andExpect(status().isBadRequest());
        }
        verify(users).suspend(id, admin);
        verify(users).reactivate(id, admin);
    }
}
