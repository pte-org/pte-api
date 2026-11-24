package com.pte.session.internal.service.staff;

import com.pte.session.internal.service.SessionLifecycleService;
import com.pte.identity.IdentityService;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ExamSessionProctor;
import com.pte.session.internal.dto.request.AssignProctorRequest;
import com.pte.session.internal.dto.response.proctor.ProctorAssignmentResponse;
import com.pte.session.internal.exception.staff.ProctorAssignmentNotFoundException;
import com.pte.session.internal.repository.ExamSessionProctorRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProctorAssignmentServiceTest {

    @Mock
    private SessionLifecycleService sessionLifecycleService;

    @Mock
    private ExamSessionProctorRepository proctorRepository;

    @Mock
    private TenancyService tenancyService;

    @Mock
    private IdentityService identityService;

    private ProctorAssignmentService proctorService;

    @BeforeEach
    void setUp() {
        proctorService = new ProctorAssignmentService(sessionLifecycleService, proctorRepository, tenancyService,
                identityService);
    }

    private ExamSession session(Long id, UUID publicId, UUID tenantId) {
        ExamSession session = new ExamSession();
        session.setId(id);
        session.setPublicId(publicId);
        session.setTenantId(tenantId);
        return session;
    }

    private CurrentUser hostAdmin(UUID tenantId) {
        return new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
    }

    @Test
    void assignProctor_saves() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID proctorPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        when(sessionLifecycleService.findOwned(sessionPublicId, caller)).thenReturn(session);
        when(proctorRepository.save(any(ExamSessionProctor.class))).thenAnswer(invocation -> {
            ExamSessionProctor assignment = invocation.getArgument(0);
            assignment.setPublicId(UUID.randomUUID());
            return assignment;
        });

        ProctorAssignmentResponse response = proctorService.assignProctor(sessionPublicId,
                new AssignProctorRequest(proctorPublicId), caller);

        assertThat(response.proctorPublicId()).isEqualTo(proctorPublicId);
    }

    @Test
    void assignProctor_rejectsIneligibleIdentityBeforeSaving() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID proctorPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        CurrentUser caller = hostAdmin(tenantId);
        when(sessionLifecycleService.findOwned(sessionPublicId, caller)).thenReturn(session);
        doThrow(new IdentityService.IdentityNotEligibleException()).when(identityService)
                .requireActiveProctorInTenant(proctorPublicId, tenantId);

        assertThatThrownBy(() -> proctorService.assignProctor(sessionPublicId,
                new AssignProctorRequest(proctorPublicId), caller))
                .isInstanceOf(IdentityService.IdentityNotEligibleException.class);

        verify(proctorRepository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void assignAtCreation_savesEveryPickedProctor() {
        UUID tenantId = UUID.randomUUID();
        ExamSession session = session(1L, UUID.randomUUID(), tenantId);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        proctorService.assignAtCreation(session, List.of(
                new AssignProctorRequest(first),
                new AssignProctorRequest(second)));

        org.mockito.ArgumentCaptor<List<ExamSessionProctor>> saved = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(proctorRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(ExamSessionProctor::getProctorPublicId)
                .containsExactly(first, second);
        assertThat(saved.getValue()).allSatisfy(assignment -> {
            assertThat(assignment.getSession()).isSameAs(session);
            assertThat(assignment.getTenantId()).isEqualTo(tenantId);
        });
    }

    @Test
    void assignAtCreation_withNoProctors_doesNothing() {
        ExamSession session = session(1L, UUID.randomUUID(), UUID.randomUUID());

        proctorService.assignAtCreation(session, null);
        proctorService.assignAtCreation(session, List.of());

        verify(proctorRepository, never()).saveAll(any());
        org.mockito.Mockito.verifyNoInteractions(identityService);
    }

    @Test
    void assignAtCreation_rejectsAnIneligibleProctorBeforeSavingAny() {
        UUID tenantId = UUID.randomUUID();
        ExamSession session = session(1L, UUID.randomUUID(), tenantId);
        UUID eligible = UUID.randomUUID();
        UUID ineligible = UUID.randomUUID();
        org.mockito.Mockito.doNothing().when(identityService).requireActiveProctorInTenant(eligible, tenantId);
        doThrow(new IdentityService.IdentityNotEligibleException()).when(identityService)
                .requireActiveProctorInTenant(ineligible, tenantId);

        assertThatThrownBy(() -> proctorService.assignAtCreation(session, List.of(
                new AssignProctorRequest(eligible), new AssignProctorRequest(ineligible))))
                .isInstanceOf(IdentityService.IdentityNotEligibleException.class);

        verify(proctorRepository, never()).saveAll(any());
    }

    @Test
    void assignAtCreation_rejectsTheSameProctorPickedTwice() {
        ExamSession session = session(1L, UUID.randomUUID(), UUID.randomUUID());
        UUID proctor = UUID.randomUUID();

        assertThatThrownBy(() -> proctorService.assignAtCreation(session, List.of(
                new AssignProctorRequest(proctor),
                new AssignProctorRequest(proctor))))
                .isInstanceOf(com.pte.session.internal.exception.staff.AlreadyAssignedException.class);

        verify(proctorRepository, never()).saveAll(any());
    }

    @Test
    void unassignProctor_fromDifferentSession_throwsNotFound_doesNotDelete() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID assignmentPublicId = UUID.randomUUID();
        ExamSession session = session(1L, sessionPublicId, tenantId);
        ExamSession otherSession = session(2L, UUID.randomUUID(), tenantId);
        CurrentUser caller = hostAdmin(tenantId);

        ExamSessionProctor assignment = new ExamSessionProctor();
        assignment.setPublicId(assignmentPublicId);
        assignment.setSession(otherSession);
        assignment.setProctorPublicId(UUID.randomUUID());

        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller)).thenReturn(session);
        when(proctorRepository.findByPublicId(assignmentPublicId)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> proctorService.unassignProctor(sessionPublicId, assignmentPublicId, caller))
                .isInstanceOf(ProctorAssignmentNotFoundException.class);

        verify(proctorRepository, never()).delete(any());
    }
}
