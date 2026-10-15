package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.identity.IdentityService;
import com.pte.proctoring.internal.mapper.ExamSessionIncidentMapper;
import com.pte.proctoring.internal.policy.ExamSessionIncidentAccessPolicy;
import com.pte.proctoring.internal.repository.ExamSessionIncidentHistoryRepository;
import com.pte.proctoring.internal.repository.ExamSessionIncidentRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamSessionIncidentServiceTest {

    @Mock
    private ExamSessionIncidentRepository incidentRepository;

    @Mock
    private ExamSessionIncidentHistoryRepository historyRepository;

    @Mock
    private IdentityService identityService;

    @Mock
    private SessionService sessionService;

    @Mock
    private AttemptService attemptService;

    @Mock
    private ExamSessionIncidentCommandService commandService;

    @Mock
    private ExamSessionIncidentMapper mapper;

    private ExamSessionIncidentQueryService queryService;
    private ExamSessionIncidentService service;
    private final UUID sessionId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        queryService = new ExamSessionIncidentQueryService(
                incidentRepository,
                historyRepository,
                identityService,
                new ExamSessionIncidentAccessPolicy(sessionService),
                mapper);
        service = new ExamSessionIncidentService(queryService, commandService);
    }

    @Test
    void hostAdminMustOwnSessionBeforeListingIncidents() {
        CurrentUser host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        when(incidentRepository.findBySessionPublicIdAndTenantIdAndDeletedFalse(
                eq(sessionId), eq(tenantId), any(PageRequest.class))).thenReturn(Page.empty());

        var result = service.listIncidents(sessionId, null, 0, 20, host);

        verify(sessionService).verifyHostAccess(sessionId, tenantId);
        assertThat(result.data()).isEmpty();
    }

    @Test
    void unassignedProctorCannotListIncidents() {
        UUID proctorId = UUID.randomUUID();
        CurrentUser proctor = new CurrentUser(proctorId, tenantId, List.of("PROCTOR"));
        when(sessionService.isProctorAssignedToSession(sessionId, proctorId, tenantId)).thenReturn(false);

        assertThatThrownBy(() -> service.listIncidents(sessionId, null, 0, 20, proctor))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(403);
        verify(incidentRepository, never()).findBySessionPublicIdAndTenantIdAndDeletedFalse(
                any(), any(), any(PageRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_ADMIN", "PLATFORM_MANAGER"})
    void platformRolesResolveSessionTenantBeforeListingIncidents(String role) {
        CurrentUser platformUser = new CurrentUser(UUID.randomUUID(), null, List.of(role));
        when(sessionService.resolveTenantId(sessionId)).thenReturn(tenantId);
        when(incidentRepository.findBySessionPublicIdAndTenantIdAndDeletedFalse(
                eq(sessionId), eq(tenantId), any(PageRequest.class))).thenReturn(Page.empty());

        var result = service.listIncidents(sessionId, null, 0, 20, platformUser);

        verify(sessionService).resolveTenantId(sessionId);
        verify(sessionService, never()).verifyHostAccess(any(), any());
        verify(sessionService, never()).isProctorAssignedToSession(any(), any(), any());
        assertThat(result.data()).isEmpty();
    }
}
