package com.pte.scheduling.service;

import com.pte.common.security.CurrentUser;
import com.pte.scheduling.domain.ExamSession;
import com.pte.scheduling.domain.ProctorAssignment;
import com.pte.scheduling.domain.enums.SessionStatus;
import com.pte.scheduling.domain.exception.ProctorContextRequiredException;
import com.pte.scheduling.dto.response.AssignedProctorSessionResponse;
import com.pte.scheduling.repository.ProctorAssignmentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProctorAssignmentQueryServiceTest {

    @Test
    void listMineScopesToCallerAndMapsRepositoryOrder() {
        ProctorAssignmentRepository repository = mock(ProctorAssignmentRepository.class);
        UUID proctorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ProctorAssignment first = assignment(
                "Morning mock",
                Instant.parse("2026-08-01T01:00:00Z"),
                SessionStatus.OPEN,
                proctorId,
                tenantId);
        ProctorAssignment second = assignment(
                "Afternoon practice",
                Instant.parse("2026-08-01T06:00:00Z"),
                SessionStatus.SCHEDULED,
                proctorId,
                tenantId);
        when(repository.findAssignedSessions(proctorId, tenantId))
                .thenReturn(List.of(first, second));
        ProctorAssignmentQueryService service =
                new ProctorAssignmentQueryService(repository);

        List<AssignedProctorSessionResponse> result = service.listMine(
                new CurrentUser(proctorId, tenantId, List.of("PROCTOR")));

        assertEquals(List.of(first.getPublicId(), second.getPublicId()),
                result.stream().map(AssignedProctorSessionResponse::assignmentPublicId).toList());
        assertEquals(List.of(first.getSession().getPublicId(), second.getSession().getPublicId()),
                result.stream().map(AssignedProctorSessionResponse::sessionPublicId).toList());
        assertEquals(List.of("Morning mock", "Afternoon practice"),
                result.stream().map(AssignedProctorSessionResponse::name).toList());
        assertEquals(List.of("OPEN", "SCHEDULED"),
                result.stream().map(AssignedProctorSessionResponse::status).toList());
        verify(repository).findAssignedSessions(proctorId, tenantId);
    }

    @Test
    void listMineRejectsNonProctorOrMissingTenantBeforeRepositoryAccess() {
        ProctorAssignmentRepository repository = mock(ProctorAssignmentRepository.class);
        ProctorAssignmentQueryService service =
                new ProctorAssignmentQueryService(repository);

        assertThrows(ProctorContextRequiredException.class, () -> service.listMine(
                new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"))));
        assertThrows(ProctorContextRequiredException.class, () -> service.listMine(
                new CurrentUser(UUID.randomUUID(), null, List.of("PROCTOR"))));
        verifyNoInteractions(repository);
    }

    private ProctorAssignment assignment(
            String name,
            Instant opensAt,
            SessionStatus status,
            UUID proctorId,
            UUID tenantId) {
        ExamSession session = new ExamSession();
        session.setPublicId(UUID.randomUUID());
        session.setName(name);
        session.setTenantId(tenantId);
        session.setOpensAt(opensAt);
        session.setClosesAt(opensAt.plusSeconds(3600));
        session.setStatus(status);
        ProctorAssignment assignment = new ProctorAssignment();
        assignment.setPublicId(UUID.randomUUID());
        assignment.setSession(session);
        assignment.setProctorPublicId(proctorId);
        assignment.setTenantId(tenantId);
        return assignment;
    }
}
