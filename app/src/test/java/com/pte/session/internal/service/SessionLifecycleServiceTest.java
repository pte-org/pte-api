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
import com.pte.session.internal.exception.NotEntitledException;
import com.pte.session.internal.exception.SessionNotClosedForReportPublicationException;
import com.pte.session.internal.exception.SessionWindowOutsideSubscriptionException;
import com.pte.session.internal.mapper.SessionMapper;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Session creation is gated by an active {@link BillingService} subscription
 * (window/capacity/overlap — ported from the subscription-billing branch)
 * before generating a fresh random exam through an in-process
 * {@link AssessmentService#generateAndPublish} call (Plan B — no
 * cache/ref table needed now that assessment lives in the same app).
 */
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
    private UUID licenseKeyId;
    private CurrentUser hostAdmin;

    @BeforeEach
    void setUp() {
        service = new SessionLifecycleService(sessionRepository, enrollmentRepository, assessmentService,
                billingService, eventPublisher);
        tenantId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        licenseKeyId = UUID.randomUUID();
        hostAdmin = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
    }

    private SnapshotResponse snapshotSummary() {
        return new SnapshotResponse(UUID.randomUUID(), "Mock Test A", 1, UUID.randomUUID(), UUID.randomUUID(), 1, null, List.of());
    }

    // ------------------------------------------------------------------
    // create — subscription gate (window/capacity/overlap)
    // ------------------------------------------------------------------

    @Test
    void create_rejectsUnknownOrInactiveSubscription_asNotFound() {
        when(billingService.getActiveSubscription(subscriptionId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(100, futureWindow()), hostAdmin))
                .isInstanceOf(SessionSubscriptionNotFoundException.class);
        verify(assessmentService, never()).generateAndPublish(any(), any(), any());
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
        verify(assessmentService, never()).generateAndPublish(any(), any(), any());
    }

    @Test
    void create_persistsGeneratedSnapshotAndSubscriptionLicense() {
        stubActiveSubscription(200);
        when(sessionRepository.findFirstOverlapping(any(), any(), any(), any())).thenReturn(Optional.empty());
        UUID snapshotId = UUID.randomUUID();
        when(assessmentService.generateAndPublish(any(), any(), any()))
                .thenReturn(new SnapshotResponse(snapshotId, "Generated", 1, UUID.randomUUID(), UUID.randomUUID(), 1, null, List.of()));
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
        when(assessmentService.generateAndPublish(any(), any(), any()))
                .thenReturn(new SnapshotResponse(UUID.randomUUID(), "Generated", 1, UUID.randomUUID(), UUID.randomUUID(), 1, null, List.of()));
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
        when(assessmentService.generateAndPublish(any(), any(), any()))
                .thenReturn(new SnapshotResponse(UUID.randomUUID(), "Generated", 1, UUID.randomUUID(), UUID.randomUUID(), 1, null, List.of()));
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

    // ------------------------------------------------------------------
    // create — lockdown-mode propagation and generation delegation (Plan B)
    // ------------------------------------------------------------------

    @Test
    void create_setsLockdownModeToNone_whenExamModeIsPractice() {
        stubActiveSubscription(200);
        when(sessionRepository.findFirstOverlapping(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(assessmentService.generateAndPublish(any(), any(), any())).thenReturn(snapshotSummary());
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            ExamSession s = invocation.getArgument(0);
            s.setPublicId(UUID.randomUUID());
            return s;
        });

        SessionResponse response = service.create(
                new CreateSessionRequest("Practice Session", subscriptionId, Set.of("SPEAKING"),
                        Instant.now().plusSeconds(3600), Instant.now().plusSeconds(5400),
                        ExamMode.PRACTICE, null, 100),
                hostAdmin);

        assertThat(response.policy().lockdownMode()).isEqualTo("NONE");
    }

    @Test
    void create_setsLockdownModeToStrict_whenExamModeIsRealExam() {
        stubActiveSubscription(200);
        when(sessionRepository.findFirstOverlapping(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(assessmentService.generateAndPublish(any(), any(), any())).thenReturn(snapshotSummary());
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            ExamSession s = invocation.getArgument(0);
            s.setPublicId(UUID.randomUUID());
            return s;
        });

        SessionResponse response = service.create(
                new CreateSessionRequest("Real Exam Session", subscriptionId, Set.of("SPEAKING"),
                        Instant.now().plusSeconds(3600), Instant.now().plusSeconds(5400),
                        ExamMode.REAL_EXAM, null, 100),
                hostAdmin);

        assertThat(response.policy().lockdownMode()).isEqualTo("STRICT");
    }

    @Test
    void create_withTeacherOverride_strictOnPractice_rejected() {
        stubActiveSubscription(200);
        when(sessionRepository.findFirstOverlapping(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(assessmentService.generateAndPublish(any(), any(), any())).thenReturn(snapshotSummary());

        assertThatThrownBy(() -> service.create(
                new CreateSessionRequest("Invalid Combo", subscriptionId, Set.of("SPEAKING"),
                        Instant.now().plusSeconds(3600), Instant.now().plusSeconds(5400),
                        ExamMode.PRACTICE, LockdownMode.STRICT, 100),
                hostAdmin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STRICT")
                .hasMessageContaining("PRACTICE");
    }

    @Test
    void create_delegatesToAssessmentGenerateAndPublish_usesReturnedSnapshotPublicId() {
        stubActiveSubscription(200);
        when(sessionRepository.findFirstOverlapping(any(), any(), any(), any())).thenReturn(Optional.empty());
        UUID canonicalSnapshotId = UUID.randomUUID();
        Set<String> skills = Set.of("SPEAKING", "WRITING");
        when(assessmentService.generateAndPublish("Session", skills, hostAdmin))
                .thenReturn(new SnapshotResponse(canonicalSnapshotId, "Mock Test A", 1, UUID.randomUUID(), UUID.randomUUID(), 1, null, List.of()));
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            ExamSession s = invocation.getArgument(0);
            s.setPublicId(UUID.randomUUID());
            return s;
        });

        SessionResponse response = service.create(
                new CreateSessionRequest("Session", subscriptionId, skills,
                        Instant.now().plusSeconds(3600), Instant.now().plusSeconds(5400),
                        ExamMode.MOCK_TEST, null, 100),
                hostAdmin);

        assertThat(response.snapshotPublicId()).isEqualTo(canonicalSnapshotId);
    }

    @Test
    void create_generationFails_noSessionSaved() {
        stubActiveSubscription(200);
        when(sessionRepository.findFirstOverlapping(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(assessmentService.generateAndPublish(any(), any(), any()))
                .thenThrow(new RuntimeException("insufficient question bank"));

        assertThatThrownBy(() -> service.create(
                new CreateSessionRequest("Session", subscriptionId, Set.of("SPEAKING"),
                        Instant.now().plusSeconds(3600), Instant.now().plusSeconds(5400),
                        ExamMode.MOCK_TEST, null, 100),
                hostAdmin))
                .isInstanceOf(RuntimeException.class);

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void create_invalidSkillCount_rejectedByValidation() {
        jakarta.validation.Validator validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();

        CreateSessionRequest empty = new CreateSessionRequest("Session", subscriptionId, Set.of(),
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), ExamMode.MOCK_TEST, null, 100);
        CreateSessionRequest tooMany = new CreateSessionRequest("Session", subscriptionId,
                Set.of("SPEAKING", "WRITING", "READING", "LISTENING", "EXTRA"),
                Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200), ExamMode.MOCK_TEST, null, 100);

        assertThat(validator.validate(empty)).isNotEmpty();
        assertThat(validator.validate(tooMany)).isNotEmpty();
    }

    // ------------------------------------------------------------------
    // changeSubscription
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // patchPolicy
    // ------------------------------------------------------------------

    @Test
    void patchPolicy_updatesLockdownMode_toStrict() {
        ExamSession session = existingSession(SessionStatus.SCHEDULED, 100);
        when(sessionRepository.findWithLockByPublicIdAndTenantId(session.getPublicId(), tenantId))
                .thenReturn(Optional.of(session));

        ExamPolicyResponse response = service.patchPolicy(session.getPublicId(),
                new PatchExamPolicyRequest(null, null, null, null, null, LockdownMode.STRICT),
                hostAdmin);

        assertThat(response.lockdownMode()).isEqualTo("STRICT");
    }

    @Test
    void patchPolicy_doesNotChangeLockdownMode_whenRequestLockdownModeIsNull() {
        ExamSession session = existingSession(SessionStatus.SCHEDULED, 100);
        session.getPolicy().setLockdownMode(LockdownMode.STRICT);
        when(sessionRepository.findWithLockByPublicIdAndTenantId(session.getPublicId(), tenantId))
                .thenReturn(Optional.of(session));

        ExamPolicyResponse response = service.patchPolicy(session.getPublicId(),
                new PatchExamPolicyRequest(ReplayPolicyType.LIMITED, 1, null, null, null, null),
                hostAdmin);

        assertThat(response.lockdownMode()).isEqualTo("STRICT");
    }

    @Test
    void toPolicy_emitsLockdownModeAsUppercaseString() {
        ExamPolicy policy = ExamPolicy.realExamDefault();

        ExamPolicyResponse response = SessionMapper.toPolicy(policy);

        assertThat(response.lockdownMode()).isEqualTo("STRICT");
        assertThat(response.lockdownMode()).matches("^[A-Z_]+$");
    }

    @Test
    void toPolicy_throwsOnIncompletePolicy_notSilentFallback() {
        ExamPolicy incomplete = new ExamPolicy();

        assertThatThrownBy(() -> SessionMapper.toPolicy(incomplete))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("incomplete");
    }

    @Test
    void closeLocksOwnedSessionRowBeforeEstablishingAttemptCutoff() {
        ExamSession session = existingSession(SessionStatus.OPEN, 100);
        when(sessionRepository.findWithLockByPublicIdAndTenantId(session.getPublicId(), tenantId))
                .thenReturn(Optional.of(session));

        service.close(session.getPublicId(), hostAdmin);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.CLOSED);
        verify(sessionRepository).findWithLockByPublicIdAndTenantId(session.getPublicId(), tenantId);
    }

    @Test
    void attemptMutationLockRejectsClosedSession() {
        ExamSession session = existingSession(SessionStatus.CLOSED, 100);
        when(sessionRepository.findWithLockByPublicIdAndTenantId(session.getPublicId(), tenantId))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.lockOpenForAttemptOperation(session.getPublicId(), tenantId))
                .isInstanceOf(NotEntitledException.class);
    }

    @Test
    void reportPublicationLockRequiresClosedSession() {
        ExamSession session = existingSession(SessionStatus.OPEN, 100);
        when(sessionRepository.findWithLockByPublicIdAndTenantId(session.getPublicId(), tenantId))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.lockClosedForReportPublication(session.getPublicId(), tenantId))
                .isInstanceOf(SessionNotClosedForReportPublicationException.class);
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

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
        return new CreateSessionRequest("Session", requestedSubscriptionId, Set.of("SPEAKING"),
                window.opensAt(), window.closesAt(), ExamMode.MOCK_TEST, null, capacity);
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
