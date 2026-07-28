package com.pte.scheduling.controller;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.SecurityClaims;
import com.pte.scheduling.dto.response.AssignedProctorSessionResponse;
import com.pte.scheduling.service.ProctorAssignmentQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyProctorAssignmentControllerTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listMineUsesOnlyAuthenticatedJwtIdentity() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        authenticate(userId, tenantId, List.of("PROCTOR"));
        ProctorAssignmentQueryService service = mock(ProctorAssignmentQueryService.class);
        AssignedProctorSessionResponse response = new AssignedProctorSessionResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Morning mock",
                Instant.parse("2026-08-01T01:00:00Z"),
                Instant.parse("2026-08-01T02:00:00Z"),
                "OPEN");
        when(service.listMine(eq(new CurrentUser(userId, tenantId, List.of("PROCTOR")))))
                .thenReturn(List.of(response));
        MyProctorAssignmentController controller =
                new MyProctorAssignmentController(service);

        var result = controller.listMine();

        assertEquals(List.of(response), result.data());
        verify(service).listMine(new CurrentUser(userId, tenantId, List.of("PROCTOR")));
    }

    @Test
    void controllerRequiresProctorRole() {
        PreAuthorize authorization =
                MyProctorAssignmentController.class.getAnnotation(PreAuthorize.class);

        assertEquals("hasRole('PROCTOR')", authorization.value());
    }

    private void authenticate(UUID userId, UUID tenantId, List<String> roles) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim(SecurityClaims.TENANT_ID, tenantId.toString())
                .claim(SecurityClaims.ROLES, roles)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
