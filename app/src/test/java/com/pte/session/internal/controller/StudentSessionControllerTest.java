package com.pte.session.internal.controller;

import com.pte.session.SessionService;
import com.pte.session.internal.dto.response.SessionResolveResponse;
import com.pte.shared.security.SecurityClaims;
import com.pte.shared.web.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentSessionControllerTest {

    private static final UUID STUDENT_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock
    private SessionService sessionService;

    private StudentSessionController controller;

    @BeforeEach
    void setUp() {
        controller = new StudentSessionController(sessionService);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(STUDENT_ID.toString())
                .claim(SecurityClaims.TENANT_ID, TENANT_ID.toString())
                .claim(SecurityClaims.ROLES, List.of("STUDENT"))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolve_delegatesWithCallerTenantAndStudentId() {
        UUID sessionPublicId = UUID.randomUUID();
        when(sessionService.resolveSessionCode("fpt-261010-k7qm", TENANT_ID, STUDENT_ID))
                .thenReturn(sessionPublicId);

        ApiResponse<SessionResolveResponse> response = controller.resolve("fpt-261010-k7qm");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo(new SessionResolveResponse(sessionPublicId));
    }

    @Test
    void controller_isRestrictedToStudents() {
        PreAuthorize preAuthorize = StudentSessionController.class.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('STUDENT')");
    }
}
