package com.pte.session.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.billing.BillingService;
import com.pte.billing.SubscriptionView;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.session.domain.Enrollment;
import com.pte.session.domain.ExamPolicy;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.LockdownMode;
import com.pte.session.domain.enums.ReplayPolicyType;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.event.SessionCancelledEvent;
import com.pte.session.dto.response.ExamPolicyResponse;
import com.pte.session.internal.dto.request.ChangeSubscriptionRequest;
import com.pte.session.internal.dto.request.CreateSessionRequest;
import com.pte.session.internal.dto.request.PatchExamPolicyRequest;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.exception.PolicyLockedException;
import com.pte.session.internal.exception.SessionSubscriptionCapacityException;
import com.pte.session.internal.exception.SessionSubscriptionNotFoundException;
import com.pte.session.internal.exception.SessionTimeConflictException;
import com.pte.session.internal.exception.SessionWindowOutsideSubscriptionException;
import com.pte.session.internal.repository.EnrollmentRepository;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionLifecycleServiceTest {

    @Mock
    private ExamSessionRepository sessionRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private AssessmentService assessmentService;

    @Mock
    private BillingService billingService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private SessionLifecycleService service;
    private UUID tenantId;
    private UUID subscriptionId;
    private UUID templateId;
    private UUID licenseKeyId;
    private CurrentUser hostAdmin;

    @BeforeEach
    void setUp() {
        service = new SessionLifecycleService(sessionRepository, enrollmentRepository, assessmentService,
                billingService, eventPublisher);
        tenantId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        templateId = UUID.randomUUID();
        licenseKeyId = UUID.randomUUID();
        hostAdmin = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
    }

    @Test
    void create_rejectsUnknownOrInactiveSubscription_asNotFound() {
        when(billingService.getActiveSubscription(subscriptionId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(100, futureWindow()), hostAdmin))
                .isInstanceOf(SessionSubscriptionNotFoundException.class);
        verify(assessmentService, never()).generateSnapshotFromTemplate(any(), any(Long.class));
    }

    @Test
    void create_rejectsWindowOutsideSubscription_asUnprocessable() {
        Instant startsAt = Instant.now().minusSeconds(60);
        SubscriptionView subscription = subscription(startsAt, Instant.now().plusSeconds(100), 200);
        when(billingService.getActiveSubscription(subscriptionId, tenantId)).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.create(request(100,
                new Window(Instant.now().plusSeconds(200), Instant.now().plusSeconds(300))), hostAdmin))
                .isInstanceOf(SessionWindowOutsideSubscriptionException.class);
    }

    @Test
    void create_rejectsCapacityAboveSubscriptionCap_withBothValues() {
        stubActiveSubscription(200);

        assertThatThrownBy(() -> service.create(request(201, futureWindow()), hostAdmin))
                .isInstanceOf(SessionSubscriptionCapacityException.class)
                .hasMessageContaining("201")
                .hasMessageContaining("200");
    }

    @Test
    void create_rejectsApplicationLevelOverlap_withConflictingSession() {
        stubActiveSubscription(200);
        UUID conflictingId = UUID.randomUUID();
        ExamSession conflicting = new ExamSession();
        conflicting.setPublicId(conflictingId);
        when(sessionRepository.findFirstOverlapping(eq(subscriptionId), any(), any(), eq(null)))
                .thenReturn(Optional.of(conflicting));

        assertThatThrownBy(() -> service.create(request(100, futureWindow()), hostAdmin))
                .isInstanceOf(SessionTimeConflictException.class)
                .hasMessageContaining(conflictingId.toString());
        verify(assessmentService, never()).generateSnapshotFromTemplate(any(), any(Long.class));
    }

    @Test
    void create_persistsGeneratedSnapshotAndSubscriptionLicense() {
        stubActiveSubscription(200);
        when(sessionRepository.findFirstOverlapping(any(), any(), any(), any())).thenReturn(Optional.empty());
        UUID snapshotId = UUID.randomUUID();
        when(assessmentService.generateSnapshotFromTemplate(eq(templateId), any(Long.class)))
                .thenReturn(new SnapshotResponse(snapshotId, "Generated", 1, UUID.randomUUID(), null, List.of()));
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            ExamSession saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        SessionResponse response = service.create(request(100, futureWindow()), hostAdmin);

        assertThat(response.snapshotPublicId()).isEqualTo(snapshotId);
        assertThat(response.subscriptionPublicId()).isEqualTo(subscriptionId);
        var captor = ArgumentCaptor.forClass(ExamSession.class);
        verify(sessionRepository).save(captor.capture());
        assertThat(captor.getValue().getLicenseKey()).isEqualTo("LIC-" + licenseKeyId);
        verify(sessionRepository).flush();
    }

    @Test
    void create_allowsSameWindowForDifferentSubscription() {
        UUID secondSubscriptionId = UUID.randomUUID();
        stubActiveSubscription(200);
        when(billingService.getActiveSubscription(secondSubscriptionId, tenantId))
                .thenReturn(Optional.of(subscription(secondSubscriptionId, 200)));
        when(sessionRepository.findFirstOverlapping(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(assessmentService.generateSnapshotFromTemplate(any(), any(Long.class)))
                .thenReturn(new SnapshotResponse(UUID.randomUUID(), "Generated", 1, UUID.randomUUID(), null, List.of()));
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            ExamSession saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });

        service.create(request(subscriptionId, 100, futureWindow()), hostAdmin);
        service.create(request(secondSubscriptionId, 100, futureWindow()), hostAdmin);

        verify(sessionRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void create_translatesDatabaseOverlapRaceToConflict() {
        stubActiveSubscription(200);
        when(sessionRepository.findFirstOverlapping(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(assessmentService.generateSnapshotFromTemplate(any(), any(Long.class)))
                .thenReturn(new SnapshotResponse(UUID.randomUUID(), "Generated", 1, UUID.randomUUID(), null, List.of()));
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            ExamSession saved = invocation.getArgument(0);
            saved.setPublicId(UUID.randomUUID());
            return saved;
        });
        doThrow(new DataIntegrityViolationException("constraint", new RuntimeException("no_overlap_per_subscription")))
                .when(sessionRepository).flush();

        assertThatThrownBy(() -> service.create(request(100, futureWindow()), hostAdmin))
                .isInstanceOf(SessionTimeConflictException.class);
    }

    @Test
    void changeSubscription_rejectsAfterSessionOpened() {
        ExamSession session = existingSession(SessionStatus.OPEN, 100);
        when(sessionRepository.findWithLockByPublicIdAndTenantId(session.getPublicId(), tenantId))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.changeSubscription(session.getPublicId(),
                new ChangeSubscriptionRequest(UUID.randomUUID()), hostAdmin))
                .isInstanceOf(PolicyLockedException.class);
    }

    @Test
    void changeSubscription_rejectsActualEnrollmentCountAboveNewCap() {
        UUID newSubscriptionId = UUID.randomUUID();
        ExamSession session = existingSession(SessionStatus.SCHEDULED, 500);
        SubscriptionView replacement = subscription(newSubscriptionId, 200);
        when(sessionRepository.findWithLockByPublicIdAndTenantId(session.getPublicId(), tenantId))
                .thenReturn(Optional.of(session));
        when(billingService.lockSubscriptions(any(), eq(tenantId)))
                .thenReturn(List.of(subscription(subscriptionId, 500), replacement));
        when(enrollmentRepository.countBySessionId(session.getId())).thenReturn(300L);

        assertThatThrownBy(() -> service.changeSubscription(session.getPublicId(),
                new ChangeSubscriptionRequest(newSubscriptionId), hostAdmin))
                .isInstanceOf(SessionSubscriptionCapacityException.class)
                .hasMessageContaining("300")
                .hasMessageContaining("200");
    }

    @Test
    void changeSubscription_updatesSessionAndAllEnrollmentLicenseKeys() {
        UUID newSubscriptionId = UUID.randomUUID();
        ExamSession session = existingSession(SessionStatus.SCHEDULED, 500);
        SubscriptionView replacement = subscription(newSubscriptionId, 500);
        Enrollment first = enrollment(session, "old-license");
        Enrollment second = enrollment(session, "old-license");
        when(sessionRepository.findWithLockByPublicIdAndTenantId(session.getPublicId(), tenantId))
                .thenReturn(Optional.of(session));
        when(billingService.lockSubscriptions(any(), eq(tenantId)))
                .thenReturn(List.of(subscription(subscriptionId, 500), replacement));
        when(enrollmentRepository.countBySessionId(session.getId())).thenReturn(2L);
        when(enrollmentRepository.findBySessionId(session.getId())).thenReturn(List.of(first, second));

        SessionResponse response = service.changeSubscription(session.getPublicId(),
                new ChangeSubscriptionRequest(newSubscriptionId), hostAdmin);

        assertThat(response.subscriptionPublicId()).isEqualTo(newSubscriptionId);
        assertThat(session.getLicenseKey()).isEqualTo(replacement.licenseKey());
        assertThat(session.getCapacity()).isEqualTo(500);
        assertThat(first.getLicenseKey()).isEqualTo(replacement.licenseKey());
        assertThat(second.getLicenseKey()).isEqualTo(replacement.licenseKey());
        verify(sessionRepository).flush();
    }

    @Test
    void changeSubscription_requestsSubscriptionLocksInPublicIdOrder() {
        UUID newSubscriptionId = UUID.randomUUID();
        ExamSession session = existingSession(SessionStatus.SCHEDULED, 100);
        SubscriptionView replacement = subscription(newSubscriptionId, 200);
        when(sessionRepository.findWithLockByPublicIdAndTenantId(session.getPublicId(), tenantId))
                .thenReturn(Optional.of(session));
        when(billingService.lockSubscriptions(any(), eq(tenantId)))
                .thenReturn(List.of(subscription(subscriptionId, 200), replacement));
        when(enrollmentRepository.countBySessionId(session.getId())).thenReturn(0L);
        when(sessionRepository.findFirstOverlapping(any(), any(), any(), any())).thenReturn(Optional.empty());

        service.changeSubscription(session.getPublicId(), new ChangeSubscriptionRequest(newSubscriptionId), hostAdmin);

        var captor = ArgumentCaptor.forClass(List.class);
        verify(billingService).lockSubscriptions(captor.capture(), eq(tenantId));
        @SuppressWarnings("unchecked")
        List<UUID> requestedOrder = (List<UUID>) captor.getValue();
        assertThat(requestedOrder).isSortedAccordingTo(Comparator.naturalOrder());
    }

    @Test
    void cancelScheduledSessionsBySubscription_cancelsOnlyScheduledAndPublishesStudents() {
        ExamSession scheduled = existingSession(SessionStatus.SCHEDULED, 100);
        ExamSession open = existingSession(SessionStatus.OPEN, 100);
        Enrollment enrollment = enrollment(scheduled, scheduled.getLicenseKey());
        when(sessionRepository.findBySubscriptionIdAndStatus(subscriptionId, SessionStatus.SCHEDULED))
                .thenReturn(List.of(scheduled));
        when(enrollmentRepository.findBySessionId(scheduled.getId())).thenReturn(List.of(enrollment));

        service.cancelScheduledSessionsBySubscription(subscriptionId);

        assertThat(scheduled.getStatus()).isEqualTo(SessionStatus.CANCELLED);
        assertThat(open.getStatus()).isEqualTo(SessionStatus.OPEN);
        var captor = ArgumentCaptor.forClass(SessionCancelledEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().studentPublicIds()).containsExactly(enrollment.getStudentPublicId());
    }

    @Test
    void patchPolicy_keepsExistingLockdownCoverage() {
        ExamSession session = existingSession(SessionStatus.SCHEDULED, 100);
        when(sessionRepository.findWithLockByPublicIdAndTenantId(session.getPublicId(), tenantId))
                .thenReturn(Optional.of(session));

        ExamPolicyResponse response = service.patchPolicy(session.getPublicId(),
                new PatchExamPolicyRequest(null, null, null, null, null, LockdownMode.STRICT), hostAdmin);

        assertThat(response.lockdownMode()).isEqualTo("STRICT");
    }

    private void stubActiveSubscription(int maxCapacity) {
        when(billingService.getActiveSubscription(subscriptionId, tenantId))
                .thenReturn(Optional.of(subscription(maxCapacity)));
    }

    private SubscriptionView subscription(int maxCapacity) {
        return subscription(Instant.now().minusSeconds(60), Instant.now().plusSeconds(7200), maxCapacity);
    }

    private SubscriptionView subscription(UUID id, int maxCapacity) {
        return new SubscriptionView(id, tenantId, UUID.randomUUID(), "LIC-" + id, Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(7200), maxCapacity, SubscriptionStatus.ACTIVE,
                ActivationSource.LICENSE_CODE);
    }

    private SubscriptionView subscription(Instant startsAt, Instant expiresAt, int maxCapacity) {
        return new SubscriptionView(subscriptionId, tenantId, UUID.randomUUID(), "LIC-" + licenseKeyId,
                startsAt, expiresAt, maxCapacity, SubscriptionStatus.ACTIVE, ActivationSource.LICENSE_CODE);
    }

    private CreateSessionRequest request(int capacity, Window window) {
        return request(subscriptionId, capacity, window);
    }

    private CreateSessionRequest request(UUID requestedSubscriptionId, int capacity, Window window) {
        return new CreateSessionRequest("Session", requestedSubscriptionId, templateId, window.opensAt(), window.closesAt(),
                ExamMode.MOCK_TEST, null, capacity);
    }

    private Window futureWindow() {
        Instant opensAt = Instant.now().plusSeconds(3600);
        return new Window(opensAt, opensAt.plusSeconds(1800));
    }

    private ExamSession existingSession(SessionStatus status, int capacity) {
        ExamSession session = new ExamSession();
        session.setId(1L);
        session.setPublicId(UUID.randomUUID());
        session.setTenantId(tenantId);
        session.setSubscriptionId(subscriptionId);
        session.setLicenseKey("LIC-" + licenseKeyId);
        session.setName("Test Session");
        session.setSnapshotPublicId(UUID.randomUUID());
        session.setOpensAt(Instant.now().plusSeconds(3600));
        session.setClosesAt(Instant.now().plusSeconds(7200));
        session.setCapacity(capacity);
        session.setStatus(status);
        session.setPolicy(ExamPolicy.mockTestDefault());
        return session;
    }

    private Enrollment enrollment(ExamSession session, String licenseKey) {
        Enrollment enrollment = new Enrollment();
        enrollment.setSession(session);
        enrollment.setStudentPublicId(UUID.randomUUID());
        enrollment.setTenantId(tenantId);
        enrollment.setLicenseKey(licenseKey);
        return enrollment;
    }

    private record Window(Instant opensAt, Instant closesAt) {
    }
}
