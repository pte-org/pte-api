package com.pte.session.internal.service;

import com.pte.enrollment.EnrollmentModuleService;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ExamSessionAudienceSource;
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.session.domain.enums.AudienceSourceType;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.dto.request.AudienceSourceRequest;
import com.pte.session.internal.dto.request.BulkEnrollRequest;
import com.pte.session.internal.dto.response.SessionClassAssignmentResponse;
import com.pte.session.internal.exception.ClassAssignmentNotAllowedException;
import com.pte.session.internal.exception.ClassAssignmentNotFoundException;
import com.pte.session.internal.repository.ExamRegistrationRepository;
import com.pte.session.internal.repository.ExamSessionAudienceSourceRepository;
import com.pte.shared.StartedAttemptLookup;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionClassSourceServiceTest {

    @Mock
    private SessionLifecycleService sessionLifecycleService;
    @Mock
    private EnrollmentService enrollmentService;
    @Mock
    private EnrollmentModuleService enrollmentModuleService;
    @Mock
    private ExamSessionAudienceSourceRepository sourceRepository;
    @Mock
    private ExamRegistrationRepository registrationRepository;
    @Mock
    private ExamAudienceService audienceService;
    @Mock
    private AudienceResolver resolver;
    @Mock
    private StartedAttemptLookup startedAttemptLookup;

    private SessionClassSourceService service;
    private UUID tenantId;
    private CurrentUser hostAdmin;
    private final UUID sessionPublicId = UUID.randomUUID();
    private final UUID classPublicId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SessionClassSourceService(sessionLifecycleService, enrollmentService,
                enrollmentModuleService, sourceRepository, registrationRepository, audienceService, resolver,
                startedAttemptLookup);
        tenantId = UUID.randomUUID();
        hostAdmin = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
    }

    private ExamSession session(SessionStatus status) {
        ExamSession session = new ExamSession();
        session.setId(1L);
        session.setPublicId(sessionPublicId);
        session.setTenantId(tenantId);
        session.setStatus(status);
        return session;
    }

    private void stubOwned(ExamSession session) {
        when(sessionLifecycleService.findOwnedWithLock(sessionPublicId, hostAdmin)).thenReturn(session);
    }

    @Test
    void assignOnScheduledExam_enrollsTheClassNow_andRecordsTheClassSource() {
        stubOwned(session(SessionStatus.SCHEDULED));
        List<UUID> students = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId)).thenReturn(students);
        when(sourceRepository.findBySessionIdAndSourceTypeAndSourcePublicId(1L, AudienceSourceType.CLASS,
                classPublicId)).thenReturn(Optional.empty());

        SessionClassAssignmentResponse response = service.assign(sessionPublicId, classPublicId, hostAdmin);

        ArgumentCaptor<BulkEnrollRequest> enrolled = ArgumentCaptor.forClass(BulkEnrollRequest.class);
        verify(enrollmentService).bulkEnroll(eq(sessionPublicId), enrolled.capture(), eq(hostAdmin));
        assertThat(enrolled.getValue().studentPublicIds()).containsExactlyElementsOf(students);
        ArgumentCaptor<ExamSessionAudienceSource> saved = ArgumentCaptor.forClass(ExamSessionAudienceSource.class);
        verify(sourceRepository).save(saved.capture());
        assertThat(saved.getValue().getSourceType()).isEqualTo(AudienceSourceType.CLASS);
        assertThat(saved.getValue().getSourcePublicId()).isEqualTo(classPublicId);
        assertThat(response.classPublicId()).isEqualTo(classPublicId);
    }

    @Test
    void assignAgain_enrollsNewMembersButDoesNotDuplicateTheSource() {
        stubOwned(session(SessionStatus.SCHEDULED));
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId))
                .thenReturn(List.of(UUID.randomUUID()));
        when(sourceRepository.findBySessionIdAndSourceTypeAndSourcePublicId(1L, AudienceSourceType.CLASS,
                classPublicId)).thenReturn(Optional.of(new ExamSessionAudienceSource()));

        service.assign(sessionPublicId, classPublicId, hostAdmin);

        verify(enrollmentService).bulkEnroll(eq(sessionPublicId), any(), eq(hostAdmin));
        verify(sourceRepository, never()).save(any());
    }

    @Test
    void assignOnDraft_goesThroughTheAudienceSourceRules() {
        stubOwned(session(SessionStatus.DRAFT));

        service.assign(sessionPublicId, classPublicId, hostAdmin);

        verify(audienceService).addSource(sessionPublicId,
                new AudienceSourceRequest(AudienceSourceType.CLASS, classPublicId), hostAdmin);
        verify(enrollmentService, never()).bulkEnroll(any(), any(), any());
    }

    @Test
    void assignOnOpenExam_isRejected() {
        stubOwned(session(SessionStatus.OPEN));

        assertThatThrownBy(() -> service.assign(sessionPublicId, classPublicId, hostAdmin))
                .isInstanceOf(ClassAssignmentNotAllowedException.class);
        verify(enrollmentService, never()).bulkEnroll(any(), any(), any());
    }

    @Test
    void list_returnsTheClassSourcesOfTheSession() {
        ExamSession session = session(SessionStatus.SCHEDULED);
        when(sessionLifecycleService.findOwned(sessionPublicId, hostAdmin)).thenReturn(session);
        ExamSessionAudienceSource source = new ExamSessionAudienceSource();
        source.setSourceType(AudienceSourceType.CLASS);
        source.setSourcePublicId(classPublicId);
        when(sourceRepository.findBySessionIdAndSourceTypeOrderByCreatedAtAsc(1L, AudienceSourceType.CLASS))
                .thenReturn(List.of(source));

        assertThat(service.list(sessionPublicId, hostAdmin)).containsExactly(
                new SessionClassAssignmentResponse(sessionPublicId, classPublicId));
    }

    @Test
    void unassignOnScheduledExam_removesTheEnrollmentOfCurrentMembersOnly() {
        stubOwned(session(SessionStatus.SCHEDULED));
        ExamSessionAudienceSource source = scheduledClassSource();
        List<UUID> members = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId)).thenReturn(members);
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(source));
        when(startedAttemptLookup.findStartedStudentPublicIds(tenantId, List.of(sessionPublicId), members))
                .thenReturn(Set.of());

        service.unassign(sessionPublicId, classPublicId, hostAdmin);

        verify(registrationRepository).deleteBySessionIdAndStudentPublicIdInAndStatus(1L, members,
                AudienceMemberStatus.ENROLLED);
        verify(sourceRepository).delete(source);
    }

    @Test
    void unassignOnScheduledExam_keepsStudentsCoveredByAnotherSourceOrAlreadyStarted() {
        stubOwned(session(SessionStatus.SCHEDULED));
        ExamSessionAudienceSource source = scheduledClassSource();
        ExamSessionAudienceSource other = new ExamSessionAudienceSource();
        other.setPublicId(UUID.randomUUID());
        other.setSourceType(AudienceSourceType.STUDENT);
        UUID covered = UUID.randomUUID();
        UUID started = UUID.randomUUID();
        UUID removable = UUID.randomUUID();
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId))
                .thenReturn(List.of(covered, started, removable));
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(source, other));
        when(resolver.resolveSource(other, tenantId)).thenReturn(List.of(covered));
        when(startedAttemptLookup.findStartedStudentPublicIds(tenantId, List.of(sessionPublicId),
                List.of(started, removable))).thenReturn(Set.of(started));

        service.unassign(sessionPublicId, classPublicId, hostAdmin);

        verify(registrationRepository).deleteBySessionIdAndStudentPublicIdInAndStatus(1L, List.of(removable),
                AudienceMemberStatus.ENROLLED);
        verify(sourceRepository).delete(source);
    }

    private ExamSessionAudienceSource scheduledClassSource() {
        ExamSessionAudienceSource source = new ExamSessionAudienceSource();
        source.setPublicId(UUID.randomUUID());
        source.setSourceType(AudienceSourceType.CLASS);
        source.setSourcePublicId(classPublicId);
        when(sourceRepository.findBySessionIdAndSourceTypeAndSourcePublicId(1L, AudienceSourceType.CLASS,
                classPublicId)).thenReturn(Optional.of(source));
        return source;
    }

    @Test
    void unassignOnDraft_goesThroughTheAudienceSourceRules() {
        stubOwned(session(SessionStatus.DRAFT));
        ExamSessionAudienceSource source = new ExamSessionAudienceSource();
        source.setPublicId(UUID.randomUUID());
        when(sourceRepository.findBySessionIdAndSourceTypeAndSourcePublicId(1L, AudienceSourceType.CLASS,
                classPublicId)).thenReturn(Optional.of(source));

        service.unassign(sessionPublicId, classPublicId, hostAdmin);

        verify(audienceService).removeSource(sessionPublicId, source.getPublicId(), hostAdmin);
        verify(registrationRepository, never()).deleteBySessionIdAndStudentPublicIdInAndStatus(any(), any(), any());
    }

    @Test
    void unassignUnknownClass_isNotFound() {
        stubOwned(session(SessionStatus.SCHEDULED));
        when(sourceRepository.findBySessionIdAndSourceTypeAndSourcePublicId(1L, AudienceSourceType.CLASS,
                classPublicId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unassign(sessionPublicId, classPublicId, hostAdmin))
                .isInstanceOf(ClassAssignmentNotFoundException.class);
    }

    @Test
    void unassignOnClosedExam_isRejected() {
        stubOwned(session(SessionStatus.CLOSED));

        assertThatThrownBy(() -> service.unassign(sessionPublicId, classPublicId, hostAdmin))
                .isInstanceOf(ClassAssignmentNotAllowedException.class);
        verify(sourceRepository, never()).delete(any());
    }
}
