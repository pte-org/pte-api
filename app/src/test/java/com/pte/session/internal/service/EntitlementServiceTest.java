package com.pte.session.internal.service;

import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.response.EntitlementResponse;
import com.pte.session.dto.response.ProctorAssignmentCheckResponse;
import com.pte.session.internal.exception.NotEntitledException;
import com.pte.session.internal.exception.ProctorNotAssignedException;
import com.pte.session.internal.repository.EnrollmentRepository;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.session.internal.repository.ProctorAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Covers the trusted application-call surface {@code attempt} (Phase 07) and {@code proctoring} (Phase 09) will use. */
@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {

    @Mock
    private ExamSessionRepository sessionRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private ProctorAssignmentRepository proctorAssignmentRepository;

    private EntitlementService entitlementService;

    @BeforeEach
    void setUp() {
        entitlementService = new EntitlementService(sessionRepository, enrollmentRepository, proctorAssignmentRepository);
    }

    private ExamSession openSession(UUID publicId, UUID tenantId) {
        ExamSession session = new ExamSession();
        session.setId(1L);
        session.setPublicId(publicId);
        session.setTenantId(tenantId);
        session.setStatus(SessionStatus.OPEN);
        session.setSnapshotPublicId(UUID.randomUUID());
        return session;
    }

    @Test
    void checkEntitlement_openSessionAndEnrolledStudent_returnsEntitlement() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        ExamSession session = openSession(sessionPublicId, UUID.randomUUID());
        when(sessionRepository.findByPublicId(sessionPublicId)).thenReturn(Optional.of(session));
        when(enrollmentRepository.existsBySessionIdAndStudentPublicId(1L, studentPublicId)).thenReturn(true);

        EntitlementResponse response = entitlementService.checkEntitlement(sessionPublicId, studentPublicId);

        assertThat(response.sessionPublicId()).isEqualTo(sessionPublicId);
        assertThat(response.snapshotPublicId()).isEqualTo(session.getSnapshotPublicId());
    }

    @Test
    void checkEntitlement_sessionNotOpen_throwsNotEntitled() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        ExamSession session = openSession(sessionPublicId, UUID.randomUUID());
        session.setStatus(SessionStatus.SCHEDULED);
        when(sessionRepository.findByPublicId(sessionPublicId)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> entitlementService.checkEntitlement(sessionPublicId, studentPublicId))
                .isInstanceOf(NotEntitledException.class);
    }

    @Test
    void checkEntitlement_studentNotEnrolled_throwsNotEntitled() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        ExamSession session = openSession(sessionPublicId, UUID.randomUUID());
        when(sessionRepository.findByPublicId(sessionPublicId)).thenReturn(Optional.of(session));
        when(enrollmentRepository.existsBySessionIdAndStudentPublicId(1L, studentPublicId)).thenReturn(false);

        assertThatThrownBy(() -> entitlementService.checkEntitlement(sessionPublicId, studentPublicId))
                .isInstanceOf(NotEntitledException.class);
    }

    @Test
    void checkEntitlement_unknownSession_throwsNotEntitled() {
        UUID sessionPublicId = UUID.randomUUID();
        when(sessionRepository.findByPublicId(sessionPublicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> entitlementService.checkEntitlement(sessionPublicId, UUID.randomUUID()))
                .isInstanceOf(NotEntitledException.class);
    }

    @Test
    void checkProctorAssignment_assignedProctor_returnsCheck() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID proctorPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ExamSession session = openSession(sessionPublicId, tenantId);
        when(sessionRepository.findByPublicId(sessionPublicId)).thenReturn(Optional.of(session));
        when(proctorAssignmentRepository.existsBySessionIdAndProctorPublicId(1L, proctorPublicId)).thenReturn(true);

        ProctorAssignmentCheckResponse response = entitlementService.checkProctorAssignment(sessionPublicId, proctorPublicId);

        assertThat(response.tenantId()).isEqualTo(tenantId);
    }

    @Test
    void checkProctorAssignment_notAssigned_throws() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID proctorPublicId = UUID.randomUUID();
        ExamSession session = openSession(sessionPublicId, UUID.randomUUID());
        when(sessionRepository.findByPublicId(sessionPublicId)).thenReturn(Optional.of(session));
        when(proctorAssignmentRepository.existsBySessionIdAndProctorPublicId(1L, proctorPublicId)).thenReturn(false);

        assertThatThrownBy(() -> entitlementService.checkProctorAssignment(sessionPublicId, proctorPublicId))
                .isInstanceOf(ProctorNotAssignedException.class);
    }

    @Test
    void verifyHostAccess_sessionFoundInTenant_doesNotThrow() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ExamSession session = openSession(sessionPublicId, tenantId);
        when(sessionRepository.findByPublicIdAndTenantId(sessionPublicId, tenantId)).thenReturn(Optional.of(session));

        // Should not throw
        entitlementService.verifyHostAccess(sessionPublicId, tenantId);
    }

    @Test
    void verifyHostAccess_sessionNotFound_throws() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(sessionRepository.findByPublicIdAndTenantId(sessionPublicId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> entitlementService.verifyHostAccess(sessionPublicId, tenantId))
                .isInstanceOf(com.pte.session.internal.exception.SessionNotFoundException.class);
    }

    @Test
    void verifyHostAccess_wrongTenant_throws() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID wrongTenantId = UUID.randomUUID();
        when(sessionRepository.findByPublicIdAndTenantId(sessionPublicId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> entitlementService.verifyHostAccess(sessionPublicId, tenantId))
                .isInstanceOf(com.pte.session.internal.exception.SessionNotFoundException.class);
    }
}
