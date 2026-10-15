package com.pte.session.internal.service;

import com.pte.session.domain.Enrollment;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.SessionAttendance;
import com.pte.session.domain.SessionAttendanceCommand;
import com.pte.session.domain.SessionIdentityVerificationCommand;
import com.pte.session.domain.enums.AttendanceStatus;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.request.RecordAttendanceRequest;
import com.pte.session.dto.request.RecordIdentityVerificationRequest;
import com.pte.session.dto.response.AttendanceCommandReceiptView;
import com.pte.session.dto.response.AttendanceRowView;
import com.pte.session.internal.repository.EnrollmentRepository;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.session.internal.repository.ProctorAssignmentRepository;
import com.pte.session.internal.repository.SessionAttendanceCommandRepository;
import com.pte.session.internal.repository.SessionAttendanceRepository;
import com.pte.session.internal.repository.SessionIdentityVerificationCommandRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceCommandServiceTest {

    @Mock
    private ExamSessionRepository examSessionRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private SessionAttendanceRepository sessionAttendanceRepository;

    @Mock
    private SessionAttendanceCommandRepository sessionAttendanceCommandRepository;

    @Mock
    private ProctorAssignmentRepository proctorAssignmentRepository;

    @Mock
    private SessionIdentityVerificationCommandRepository sessionIdentityVerificationCommandRepository;

    private Clock clock;
    private AttendanceCommandService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionPublicId = UUID.randomUUID();
    private final UUID studentPublicId = UUID.randomUUID();
    private final UUID proctorId = UUID.randomUUID();
    private final UUID oldEnrollmentPublicId = UUID.randomUUID();
    private final UUID newEnrollmentPublicId = UUID.randomUUID();

    private ExamSession session;
    private Enrollment newEnrollment;
    private CurrentUser proctorCaller;

    @BeforeEach
    void setUp() {
        Instant fixedInstant = Instant.parse("2026-10-13T10:00:00Z");
        clock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        service = new AttendanceCommandService(
                examSessionRepository,
                enrollmentRepository,
                sessionAttendanceRepository,
                sessionAttendanceCommandRepository,
                proctorAssignmentRepository,
                sessionIdentityVerificationCommandRepository,
                clock
        );

        session = new ExamSession();
        session.setId(100L);
        session.setPublicId(sessionPublicId);
        session.setTenantId(tenantId);
        session.setStatus(SessionStatus.OPEN);
        session.setOpensAt(fixedInstant.minusSeconds(3600));
        session.setClosesAt(fixedInstant.plusSeconds(3600));

        newEnrollment = new Enrollment();
        newEnrollment.setId(200L);
        newEnrollment.setPublicId(newEnrollmentPublicId);
        newEnrollment.setSession(session);
        newEnrollment.setStudentPublicId(studentPublicId);
        newEnrollment.setTenantId(tenantId);

        proctorCaller = new CurrentUser(proctorId, tenantId, List.of("ROLE_PROCTOR"));
    }

    @Test
    void recordIdentityVerification_WhenReEnrolled_ResetsOldAttendanceStatusAndNote_AndIncrementsVersion() {
        // Arrange
        when(examSessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));
        when(proctorAssignmentRepository.isProctorAssigned(sessionPublicId, proctorId, tenantId))
                .thenReturn(true);
        when(enrollmentRepository.findActiveEnrollment(sessionPublicId, studentPublicId, tenantId))
                .thenReturn(Optional.of(newEnrollment));

        SessionAttendance existingRow = new SessionAttendance();
        existingRow.setId(1L);
        existingRow.setSession(session);
        existingRow.setTenantId(tenantId);
        existingRow.setStudentPublicId(studentPublicId);
        existingRow.setEnrollmentPublicId(oldEnrollmentPublicId);
        existingRow.setStatus(AttendanceStatus.PRESENT);
        existingRow.setLastReasonOrNote("Old attendance marked present");
        existingRow.setIdentityStatus("VERIFIED");
        existingRow.setIdentityNote("Old identity card verified");
        existingRow.setVersion(2L);

        when(sessionAttendanceRepository.findBySessionIdAndStudentPublicId(session.getId(), studentPublicId))
                .thenReturn(Optional.of(existingRow));

        UUID requestPublicId = UUID.randomUUID();
        RecordIdentityVerificationRequest request = new RecordIdentityVerificationRequest(
                "VERIFIED",
                newEnrollmentPublicId,
                2L,
                requestPublicId,
                "New passport verified"
        );

        // Act
        AttendanceRowView snapshot = service.recordIdentityVerification(
                sessionPublicId,
                studentPublicId,
                request,
                proctorCaller,
                false
        );

        // Assert
        ArgumentCaptor<SessionAttendance> rowCaptor = ArgumentCaptor.forClass(SessionAttendance.class);
        verify(sessionAttendanceRepository).save(rowCaptor.capture());
        SessionAttendance savedRow = rowCaptor.getValue();

        assertThat(savedRow.getEnrollmentPublicId()).isEqualTo(newEnrollmentPublicId);
        assertThat(savedRow.getStatus()).isEqualTo(AttendanceStatus.UNMARKED);
        assertThat(savedRow.getLastReasonOrNote()).isNull();
        assertThat(savedRow.getIdentityStatus()).isEqualTo("VERIFIED");
        assertThat(savedRow.getIdentityNote()).isEqualTo("New passport verified");
        assertThat(savedRow.getVersion()).isEqualTo(3L);

        ArgumentCaptor<SessionIdentityVerificationCommand> cmdCaptor =
                ArgumentCaptor.forClass(SessionIdentityVerificationCommand.class);
        verify(sessionIdentityVerificationCommandRepository).save(cmdCaptor.capture());
        SessionIdentityVerificationCommand savedCmd = cmdCaptor.getValue();

        assertThat(savedCmd.getEnrollmentPublicId()).isEqualTo(newEnrollmentPublicId);
        assertThat(savedCmd.getExpectedVersion()).isEqualTo(2L);
        assertThat(savedCmd.getAppliedVersion()).isEqualTo(3L);
        assertThat(savedCmd.getChanged()).isTrue();
        assertThat(savedCmd.getCommittedResult()).isEqualTo("SUCCESS");

        assertThat(snapshot.status()).isEqualTo(AttendanceStatus.UNMARKED);
        assertThat(snapshot.lastReasonOrNote()).isNull();
        assertThat(snapshot.identityStatus()).isEqualTo("VERIFIED");
        assertThat(snapshot.identityNote()).isEqualTo("New passport verified");
        assertThat(snapshot.version()).isEqualTo(3L);
    }

    @Test
    void recordAttendance_WhenReEnrolled_ResetsOldIdentityStatusAndNote_AndIncrementsVersion() {
        // Arrange
        when(examSessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));
        when(proctorAssignmentRepository.isProctorAssigned(sessionPublicId, proctorId, tenantId))
                .thenReturn(true);
        when(enrollmentRepository.findActiveEnrollment(sessionPublicId, studentPublicId, tenantId))
                .thenReturn(Optional.of(newEnrollment));

        SessionAttendance existingRow = new SessionAttendance();
        existingRow.setId(1L);
        existingRow.setSession(session);
        existingRow.setTenantId(tenantId);
        existingRow.setStudentPublicId(studentPublicId);
        existingRow.setEnrollmentPublicId(oldEnrollmentPublicId);
        existingRow.setStatus(AttendanceStatus.PRESENT);
        existingRow.setLastReasonOrNote("Old attendance note");
        existingRow.setIdentityStatus("VERIFIED");
        existingRow.setIdentityNote("Old identity verified");
        existingRow.setVersion(2L);

        when(sessionAttendanceRepository.findBySessionIdAndStudentPublicId(session.getId(), studentPublicId))
                .thenReturn(Optional.of(existingRow));

        UUID requestPublicId = UUID.randomUUID();
        RecordAttendanceRequest request = new RecordAttendanceRequest(
                AttendanceStatus.PRESENT,
                newEnrollmentPublicId,
                2L,
                requestPublicId,
                "New attendance checked in"
        );

        // Act
        AttendanceCommandReceiptView receipt = service.recordAttendance(
                sessionPublicId,
                studentPublicId,
                request,
                proctorCaller,
                false
        );

        // Assert
        ArgumentCaptor<SessionAttendance> rowCaptor = ArgumentCaptor.forClass(SessionAttendance.class);
        verify(sessionAttendanceRepository).save(rowCaptor.capture());
        SessionAttendance savedRow = rowCaptor.getValue();

        assertThat(savedRow.getEnrollmentPublicId()).isEqualTo(newEnrollmentPublicId);
        assertThat(savedRow.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(savedRow.getLastReasonOrNote()).isEqualTo("New attendance checked in");
        assertThat(savedRow.getIdentityStatus()).isEqualTo("UNVERIFIED");
        assertThat(savedRow.getIdentityNote()).isNull();
        assertThat(savedRow.getVersion()).isEqualTo(3L);

        ArgumentCaptor<SessionAttendanceCommand> cmdCaptor =
                ArgumentCaptor.forClass(SessionAttendanceCommand.class);
        verify(sessionAttendanceCommandRepository).save(cmdCaptor.capture());
        SessionAttendanceCommand savedCmd = cmdCaptor.getValue();

        assertThat(savedCmd.getEnrollmentPublicId()).isEqualTo(newEnrollmentPublicId);
        assertThat(savedCmd.getExpectedVersion()).isEqualTo(2L);
        assertThat(savedCmd.getAppliedVersion()).isEqualTo(3L);
        assertThat(savedCmd.getChanged()).isTrue();
        assertThat(savedCmd.getCommittedResult()).isEqualTo("SUCCESS");

        assertThat(receipt.currentAttendance().status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(receipt.currentAttendance().identityStatus()).isEqualTo("UNVERIFIED");
        assertThat(receipt.currentAttendance().identityNote()).isNull();
        assertThat(receipt.currentAttendance().version()).isEqualTo(3L);
    }

    @Test
    void recordAttendance_WhenReEnrolled_MarkingUnmarkedIsNotNoOp_ResetsOldIdentityAndBindsNewEnrollment() {
        // Arrange
        when(examSessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));
        when(proctorAssignmentRepository.isProctorAssigned(sessionPublicId, proctorId, tenantId))
                .thenReturn(true);
        when(enrollmentRepository.findActiveEnrollment(sessionPublicId, studentPublicId, tenantId))
                .thenReturn(Optional.of(newEnrollment));

        SessionAttendance existingRow = new SessionAttendance();
        existingRow.setId(1L);
        existingRow.setSession(session);
        existingRow.setTenantId(tenantId);
        existingRow.setStudentPublicId(studentPublicId);
        existingRow.setEnrollmentPublicId(oldEnrollmentPublicId);
        existingRow.setStatus(AttendanceStatus.PRESENT);
        existingRow.setLastReasonOrNote("Old attendance note");
        existingRow.setIdentityStatus("VERIFIED");
        existingRow.setIdentityNote("Old identity note");
        existingRow.setVersion(2L);

        when(sessionAttendanceRepository.findBySessionIdAndStudentPublicId(session.getId(), studentPublicId))
                .thenReturn(Optional.of(existingRow));

        UUID requestPublicId = UUID.randomUUID();
        RecordAttendanceRequest request = new RecordAttendanceRequest(
                AttendanceStatus.UNMARKED,
                newEnrollmentPublicId,
                2L,
                requestPublicId,
                null
        );

        // Act
        AttendanceCommandReceiptView receipt = service.recordAttendance(
                sessionPublicId,
                studentPublicId,
                request,
                proctorCaller,
                false
        );

        // Assert
        ArgumentCaptor<SessionAttendance> rowCaptor = ArgumentCaptor.forClass(SessionAttendance.class);
        verify(sessionAttendanceRepository).save(rowCaptor.capture());
        SessionAttendance savedRow = rowCaptor.getValue();

        assertThat(savedRow.getEnrollmentPublicId()).isEqualTo(newEnrollmentPublicId);
        assertThat(savedRow.getStatus()).isEqualTo(AttendanceStatus.UNMARKED);
        assertThat(savedRow.getIdentityStatus()).isEqualTo("UNVERIFIED");
        assertThat(savedRow.getIdentityNote()).isNull();
        assertThat(savedRow.getVersion()).isEqualTo(3L);

        ArgumentCaptor<SessionAttendanceCommand> cmdCaptor =
                ArgumentCaptor.forClass(SessionAttendanceCommand.class);
        verify(sessionAttendanceCommandRepository).save(cmdCaptor.capture());
        SessionAttendanceCommand savedCmd = cmdCaptor.getValue();

        assertThat(savedCmd.getChanged()).isTrue();
        assertThat(savedCmd.getCommittedResult()).isEqualTo("SUCCESS");
        assertThat(savedCmd.getAppliedVersion()).isEqualTo(3L);

        assertThat(receipt.currentAttendance().status()).isEqualTo(AttendanceStatus.UNMARKED);
        assertThat(receipt.currentAttendance().identityStatus()).isEqualTo("UNVERIFIED");
        assertThat(receipt.currentAttendance().identityNote()).isNull();
        assertThat(receipt.currentAttendance().version()).isEqualTo(3L);
    }

    @Test
    void recordAttendance_WhenSameEnrollment_PreservesExistingIdentityVerification() {
        // Arrange
        when(examSessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));
        when(proctorAssignmentRepository.isProctorAssigned(sessionPublicId, proctorId, tenantId))
                .thenReturn(true);
        when(enrollmentRepository.findActiveEnrollment(sessionPublicId, studentPublicId, tenantId))
                .thenReturn(Optional.of(newEnrollment));

        SessionAttendance existingRow = new SessionAttendance();
        existingRow.setId(1L);
        existingRow.setSession(session);
        existingRow.setTenantId(tenantId);
        existingRow.setStudentPublicId(studentPublicId);
        existingRow.setEnrollmentPublicId(newEnrollmentPublicId); // Already bound to new enrollment
        existingRow.setStatus(AttendanceStatus.UNMARKED);
        existingRow.setLastReasonOrNote(null);
        existingRow.setIdentityStatus("VERIFIED");
        existingRow.setIdentityNote("Verified on new enrollment");
        existingRow.setVersion(3L);

        when(sessionAttendanceRepository.findBySessionIdAndStudentPublicId(session.getId(), studentPublicId))
                .thenReturn(Optional.of(existingRow));

        UUID requestPublicId = UUID.randomUUID();
        RecordAttendanceRequest request = new RecordAttendanceRequest(
                AttendanceStatus.PRESENT,
                newEnrollmentPublicId,
                3L,
                requestPublicId,
                "Student arrived"
        );

        // Act
        AttendanceCommandReceiptView receipt = service.recordAttendance(
                sessionPublicId,
                studentPublicId,
                request,
                proctorCaller,
                false
        );

        // Assert
        ArgumentCaptor<SessionAttendance> rowCaptor = ArgumentCaptor.forClass(SessionAttendance.class);
        verify(sessionAttendanceRepository).save(rowCaptor.capture());
        SessionAttendance savedRow = rowCaptor.getValue();

        assertThat(savedRow.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(savedRow.getIdentityStatus()).isEqualTo("VERIFIED");
        assertThat(savedRow.getIdentityNote()).isEqualTo("Verified on new enrollment");
        assertThat(savedRow.getVersion()).isEqualTo(4L);

        assertThat(receipt.currentAttendance().status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(receipt.currentAttendance().identityStatus()).isEqualTo("VERIFIED");
        assertThat(receipt.currentAttendance().identityNote()).isEqualTo("Verified on new enrollment");
        assertThat(receipt.currentAttendance().version()).isEqualTo(4L);
    }

    @Test
    void recordIdentityVerification_WhenSameEnrollment_PreservesExistingAttendanceStatus() {
        // Arrange
        when(examSessionRepository.findWithLockByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));
        when(proctorAssignmentRepository.isProctorAssigned(sessionPublicId, proctorId, tenantId))
                .thenReturn(true);
        when(enrollmentRepository.findActiveEnrollment(sessionPublicId, studentPublicId, tenantId))
                .thenReturn(Optional.of(newEnrollment));

        SessionAttendance existingRow = new SessionAttendance();
        existingRow.setId(1L);
        existingRow.setSession(session);
        existingRow.setTenantId(tenantId);
        existingRow.setStudentPublicId(studentPublicId);
        existingRow.setEnrollmentPublicId(newEnrollmentPublicId); // Already bound to new enrollment
        existingRow.setStatus(AttendanceStatus.PRESENT);
        existingRow.setLastReasonOrNote("Attendance checked in");
        existingRow.setIdentityStatus("UNVERIFIED");
        existingRow.setIdentityNote(null);
        existingRow.setVersion(3L);

        when(sessionAttendanceRepository.findBySessionIdAndStudentPublicId(session.getId(), studentPublicId))
                .thenReturn(Optional.of(existingRow));

        UUID requestPublicId = UUID.randomUUID();
        RecordIdentityVerificationRequest request = new RecordIdentityVerificationRequest(
                "VERIFIED",
                newEnrollmentPublicId,
                3L,
                requestPublicId,
                "ID verified after attendance"
        );

        // Act
        AttendanceRowView snapshot = service.recordIdentityVerification(
                sessionPublicId,
                studentPublicId,
                request,
                proctorCaller,
                false
        );

        // Assert
        ArgumentCaptor<SessionAttendance> rowCaptor = ArgumentCaptor.forClass(SessionAttendance.class);
        verify(sessionAttendanceRepository).save(rowCaptor.capture());
        SessionAttendance savedRow = rowCaptor.getValue();

        assertThat(savedRow.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(savedRow.getLastReasonOrNote()).isEqualTo("Attendance checked in");
        assertThat(savedRow.getIdentityStatus()).isEqualTo("VERIFIED");
        assertThat(savedRow.getIdentityNote()).isEqualTo("ID verified after attendance");
        assertThat(savedRow.getVersion()).isEqualTo(4L);

        assertThat(snapshot.status()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(snapshot.identityStatus()).isEqualTo("VERIFIED");
        assertThat(snapshot.version()).isEqualTo(4L);
    }

    @Test
    void getReceipt_WhenReEnrolled_BuildCurrentSnapshotReturnsUnmarkedAndUnverified() {
        // Arrange
        when(proctorAssignmentRepository.isProctorAssigned(sessionPublicId, proctorId, tenantId))
                .thenReturn(true);

        SessionAttendanceCommand cmd = new SessionAttendanceCommand();
        UUID cmdRequestPublicId = UUID.randomUUID();
        cmd.setRequestPublicId(cmdRequestPublicId);
        cmd.setStudentPublicId(studentPublicId);
        cmd.setEnrollmentPublicId(oldEnrollmentPublicId);
        cmd.setActorPublicId(proctorId);
        cmd.setActorRole("PROCTOR");
        cmd.setAppliedVersion(2L);
        cmd.setPreviousStatus(AttendanceStatus.UNMARKED);
        cmd.setNewStatus(AttendanceStatus.PRESENT);
        cmd.setServerActionAt(Instant.parse("2026-10-13T09:30:00Z"));
        cmd.setChanged(true);
        cmd.setCommittedResult("SUCCESS");

        when(sessionAttendanceCommandRepository.findReceipt(tenantId, sessionPublicId, proctorId, cmdRequestPublicId))
                .thenReturn(Optional.of(cmd));
        when(examSessionRepository.findByPublicIdAndTenantId(sessionPublicId, tenantId))
                .thenReturn(Optional.of(session));
        when(enrollmentRepository.findActiveEnrollment(sessionPublicId, studentPublicId, tenantId))
                .thenReturn(Optional.of(newEnrollment));

        SessionAttendance oldRow = new SessionAttendance();
        oldRow.setId(1L);
        oldRow.setSession(session);
        oldRow.setTenantId(tenantId);
        oldRow.setStudentPublicId(studentPublicId);
        oldRow.setEnrollmentPublicId(oldEnrollmentPublicId); // Old enrollment
        oldRow.setStatus(AttendanceStatus.PRESENT);
        oldRow.setLastReasonOrNote("Old reason");
        oldRow.setIdentityStatus("VERIFIED");
        oldRow.setIdentityNote("Old note");
        oldRow.setVersion(2L);

        when(sessionAttendanceRepository.findBySessionIdAndStudentPublicId(session.getId(), studentPublicId))
                .thenReturn(Optional.of(oldRow));

        // Act
        AttendanceCommandReceiptView receipt = service.getReceipt(
                sessionPublicId,
                cmdRequestPublicId,
                proctorCaller,
                false
        );

        // Assert - currentAttendance snapshot reflects new enrollment, which has UNMARKED and UNVERIFIED
        assertThat(receipt.currentAttendance().enrollmentPublicId()).isEqualTo(newEnrollmentPublicId);
        assertThat(receipt.currentAttendance().status()).isEqualTo(AttendanceStatus.UNMARKED);
        assertThat(receipt.currentAttendance().lastReasonOrNote()).isNull();
        assertThat(receipt.currentAttendance().identityStatus()).isEqualTo("UNVERIFIED");
        assertThat(receipt.currentAttendance().identityNote()).isNull();
        assertThat(receipt.currentAttendance().version()).isEqualTo(2L);
    }
}
