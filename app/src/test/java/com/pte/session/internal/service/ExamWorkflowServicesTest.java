package com.pte.session.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.billing.BillingService;
import com.pte.billing.SubscriptionView;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.enrollment.EnrollmentModuleService;
import com.pte.identity.IdentityService;
import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.session.domain.ExamPolicy;
import com.pte.session.domain.ExamRegistration;
import com.pte.session.domain.ExamSessionAudienceSource;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.AudienceDecisionReason;
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.session.domain.enums.AudienceSourceType;
import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.ReusePolicy;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.dto.request.AudienceSourceRequest;
import com.pte.session.internal.dto.response.AudienceSourceResponse;
import com.pte.session.internal.dto.request.CreateExamDraftRequest;
import com.pte.session.internal.dto.request.PatchExamDraftRequest;
import com.pte.session.internal.dto.response.AudienceMemberResponse;
import com.pte.session.internal.dto.response.BulkEnrollResponse;
import com.pte.session.dto.response.SlotConflict;
import com.pte.session.internal.exception.ExamAudienceConflictException;
import com.pte.session.internal.exception.AudienceSourceAlreadyCoveredException;
import com.pte.session.internal.exception.ExamDraftConfigurationException;
import com.pte.session.internal.exception.ExamDraftNotDeletableException;
import com.pte.session.internal.exception.ExamDraftVersionConflictException;
import com.pte.session.internal.repository.ExamRegistrationRepository;
import com.pte.session.internal.repository.ExamSessionAudienceSourceRepository;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.shared.StartedAttemptLookup;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.exception.DomainException;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.internal.exception.TenantSuspendedException;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamWorkflowServicesTest {

    @Mock
    private ExamSessionRepository sessionRepository;
    @Mock
    private ExamSessionAudienceSourceRepository sourceRepository;
    @Mock
    private ExamRegistrationRepository registrationRepository;
    @Mock
    private SessionLifecycleService sessionLifecycleService;
    @Mock
    private EnrollmentService enrollmentService;
    @Mock
    private AssessmentService assessmentService;
    @Mock
    private ExamDefinitionService examDefinitionService;
    @Mock
    private BillingService billingService;
    @Mock
    private EnrollmentModuleService enrollmentModuleService;
    @Mock
    private IdentityService identityService;
    @Mock
    private StartedAttemptLookup startedAttemptLookup;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private SessionCodeGenerator sessionCodeGenerator;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private TenancyService tenancyService;

    private ExamDraftService draftService;
    private ExamAudienceService audienceService;
    private ExamPublishService publishService;
    private UUID tenantId;
    private UUID templateId;
    private UUID subscriptionId;
    private CurrentUser hostAdmin;
    private Instant opensAt;
    private Instant closesAt;

    @BeforeEach
    void setUp() {
        ExamSessionAccess access = new ExamSessionAccess(sessionRepository, sessionLifecycleService, billingService,
                tenancyService, auditLogService);
        AudienceResolver resolver = new AudienceResolver(sourceRepository, registrationRepository,
                enrollmentModuleService, identityService, startedAttemptLookup);
        draftService = new ExamDraftService(sessionRepository, access, new ExamSkillSelection(), sessionLifecycleService,
                examDefinitionService, billingService, sessionCodeGenerator);
        audienceService = new ExamAudienceService(sourceRepository, registrationRepository, access, resolver,
                enrollmentModuleService);
        publishService = new ExamPublishService(sessionRepository, access, sessionLifecycleService,
                enrollmentService, assessmentService, billingService, resolver, registrationRepository,
                eventPublisher);
        tenantId = UUID.randomUUID();
        templateId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        hostAdmin = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        opensAt = Instant.now().plusSeconds(3600);
        closesAt = opensAt.plusSeconds(3600);
    }

    @Test
    void createDraft_suspendedTenantIsBlockedBeforeCommercialSideEffects() {
        doThrow(new TenantSuspendedException()).when(tenancyService).assertTenantActive(tenantId);

        assertThatThrownBy(() -> draftService.createDraft(new CreateExamDraftRequest(
                "HK1 mock", templateId, subscriptionId, opensAt, closesAt,
                ExamMode.OFFICIAL_EXAM, null, null, 50), hostAdmin))
                .isInstanceOf(TenantSuspendedException.class);

        verifyNoInteractions(examDefinitionService, billingService, sessionRepository);
    }

    @Test
    void createDraft_defaultsOfficialExamToStartedSeriesExclusion() {
        stubTemplateAndSubscription();
        when(sessionCodeGenerator.generate(tenantId, opensAt)).thenReturn("FPT-261010-K7QM");
        when(sessionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            var session = invocation.getArgument(0, com.pte.session.domain.ExamSession.class);
            session.setPublicId(UUID.randomUUID());
            session.setId(10L);
            return session;
        });

        var response = draftService.createDraft(new CreateExamDraftRequest(
                "  HK1 mock  ", templateId, subscriptionId, opensAt, closesAt,
                ExamMode.OFFICIAL_EXAM, null, " HK1-2026 ", 50), hostAdmin);

        assertThat(response.status()).isEqualTo(SessionStatus.DRAFT.name());
        assertThat(response.reusePolicy()).isEqualTo(ReusePolicy.EXCLUDE_STARTED_IN_SERIES);
        assertThat(response.seriesKey()).isEqualTo("HK1-2026");
        assertThat(response.examDefinitionPublicId()).isEqualTo(templateId);
        assertThat(response.examDefinitionVersion()).isEqualTo(3);
        assertThat(response.policy().lockdownMode()).isEqualTo("STRICT");
        assertThat(response.sessionCode()).isEqualTo("FPT-261010-K7QM");
        verify(auditLogService).record(hostAdmin, SessionConstants.AGGREGATE_EXAM_SESSION,
                response.publicId().toString(), "HK1 mock", "FPT-261010-K7QM",
                SessionConstants.EVENT_EXAM_DRAFT_CREATED, "HK1 mock");
    }

    @Test
    void createDraft_partialSkillSelection_isRejected() {
        when(examDefinitionService.findActiveByPublicId(templateId)).thenReturn(Optional.of(
                twoSkillTemplateResponse()));
        stubSubscriptionForDraft();

        assertThatThrownBy(() -> draftService.createDraft(new CreateExamDraftRequest(
                "Speaking only", templateId, subscriptionId, opensAt, closesAt,
                ExamMode.OFFICIAL_EXAM, null, "SERIES", 50, List.of("SPEAKING"), null), hostAdmin))
                .isInstanceOf(ExamDraftConfigurationException.class)
                .extracting(ex -> ((DomainException) ex).getUserMessage())
                .isEqualTo(SessionConstants.SKILLS_FULL_TEMPLATE_REQUIRED);

        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createDraft_officialWithNonStrictLockdown_isRejected() {
        stubTemplateAndSubscription();

        assertThatThrownBy(() -> draftService.createDraft(new CreateExamDraftRequest(
                "Invalid official", templateId, subscriptionId, opensAt, closesAt,
                ExamMode.OFFICIAL_EXAM, null, "SERIES", 50, null, null,
                com.pte.session.domain.enums.LockdownMode.STANDARD), hostAdmin))
                .isInstanceOf(com.pte.session.internal.exception.InvalidLockdownModeException.class);

        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createDraft_withRetries_isRejectedBecauseEveryStudentSitsOnce() {
        stubTemplateAndSubscription();

        assertThatThrownBy(() -> draftService.createDraft(new CreateExamDraftRequest(
                "Retries", templateId, subscriptionId, opensAt, closesAt,
                ExamMode.OFFICIAL_EXAM, null, "SERIES", 50, null, 2,
                com.pte.session.domain.enums.LockdownMode.STRICT), hostAdmin))
                .isInstanceOf(com.pte.session.internal.exception.ExamDraftConfigurationException.class);

        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateDraft_withoutLockdown_preservesStrictAfterRetryPatch() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);
        when(examDefinitionService.getByPublicId(templateId)).thenReturn(templateResponse());
        stubSubscriptionForDraft();
        when(sessionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = draftService.updateDraft(session.getPublicId(), new PatchExamDraftRequest(
                "Renamed", null, null, null, null, null, null, null, null,
                0L, null, 0, null), hostAdmin);

        assertThat(session.getMaxRetriesPerStudent()).isZero();
        assertThat(response.policy().lockdownMode()).isEqualTo("STRICT");
    }

    @Test
    void updateDraft_withExplicitNonStrictLockdown_isRejectedWithoutSaving() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);
        stubSubscriptionForDraft();

        assertThatThrownBy(() -> draftService.updateDraft(session.getPublicId(), new PatchExamDraftRequest(
                null, null, null, null, null, null, null, null, null,
                0L, null, null, com.pte.session.domain.enums.LockdownMode.STANDARD), hostAdmin))
                .isInstanceOf(com.pte.session.internal.exception.InvalidLockdownModeException.class);

        assertThat(session.getPolicy().getLockdownMode())
                .isEqualTo(com.pte.session.domain.enums.LockdownMode.STRICT);
        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateDraft_legacyDraftWithoutModeOrLockdown_derivesOfficialStrictDefault() {
        ExamSession session = draftSession(null, null);
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);
        when(examDefinitionService.getByPublicId(templateId)).thenReturn(templateResponse());
        stubSubscriptionForDraft();
        when(sessionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = draftService.updateDraft(session.getPublicId(), new PatchExamDraftRequest(
                null, null, null, null, null, ExamMode.OFFICIAL_EXAM, null, null, null,
                0L, null, null, null), hostAdmin);

        assertThat(session.getExamMode()).isEqualTo(ExamMode.OFFICIAL_EXAM);
        assertThat(response.policy().lockdownMode()).isEqualTo("STRICT");
    }

    @Test
    void updateDraft_rescheduleKeepsSessionCode() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        session.setSessionCode("FPT-261010-K7QM");
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);
        when(examDefinitionService.getByPublicId(templateId)).thenReturn(templateResponse());
        stubSubscriptionForDraft();
        when(sessionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = draftService.updateDraft(session.getPublicId(), new PatchExamDraftRequest(
                null, null, null, opensAt.plusSeconds(1800), closesAt.plusSeconds(1800), null, null, null,
                null, 0L), hostAdmin);

        assertThat(session.getOpensAt()).isEqualTo(opensAt.plusSeconds(1800));
        assertThat(response.sessionCode()).isEqualTo("FPT-261010-K7QM");
        verify(sessionCodeGenerator, never()).generate(any(), any());
    }

    @Test
    void updateDraft_rejectsStaleOptimisticVersion() {
        var session = new com.pte.session.domain.ExamSession();
        session.setPublicId(UUID.randomUUID());
        session.setTenantId(tenantId);
        session.setStatus(SessionStatus.DRAFT);
        session.setDraftVersion(7L);
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);

        assertThatThrownBy(() -> draftService.updateDraft(session.getPublicId(),
                new PatchExamDraftRequest("Renamed", null, null, null, null, null, null, null,
                        null, 6L), hostAdmin))
                .isInstanceOf(ExamDraftVersionConflictException.class);

        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteDraft_softDeletesDraftAndRecordsAuditEvent() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);

        draftService.deleteDraft(session.getPublicId(), hostAdmin);

        assertThat(session.isDeleted()).isTrue();
        verify(sessionRepository).save(session);
        verify(auditLogService).record(hostAdmin, SessionConstants.AGGREGATE_EXAM_SESSION,
                session.getPublicId().toString(), session.getName(), session.getSessionCode(),
                SessionConstants.EVENT_EXAM_DRAFT_DELETED, session.getName());
    }

    @Test
    void deleteDraft_rejectsPublishedSession() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        session.setStatus(SessionStatus.SCHEDULED);
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);

        assertThatThrownBy(() -> draftService.deleteDraft(session.getPublicId(), hostAdmin))
                .isInstanceOf(ExamDraftNotDeletableException.class);

        assertThat(session.isDeleted()).isFalse();
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void cancelDraft_isRejectedBecauseDraftUsesDelete() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);

        assertThatThrownBy(() -> publishService.cancel(session.getPublicId(), hostAdmin))
                .isInstanceOf(com.pte.session.internal.exception.SessionNotCancellableException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void cancelScheduled_publishesNeutralCancellationEvent() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        session.setStatus(SessionStatus.SCHEDULED);
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);

        var response = publishService.cancel(session.getPublicId(), hostAdmin);

        assertThat(response.status()).isEqualTo(SessionStatus.CANCELLED.name());
        verify(eventPublisher).publishEvent(any(com.pte.session.dto.event.SessionCancelledEvent.class));
    }

    @Test
    void previewAudience_isReadOnlyAndDoesNotPersistComputedMembers() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        ExamSession session = new ExamSession();
        session.setId(20L);
        session.setPublicId(sessionPublicId);
        session.setTenantId(tenantId);
        session.setStatus(SessionStatus.DRAFT);
        session.setCapacity(10);

        ExamSessionAudienceSource source = new ExamSessionAudienceSource();
        source.setSession(session);
        source.setTenantId(tenantId);
        source.setSourceType(AudienceSourceType.CLASS);
        source.setSourcePublicId(classPublicId);

        ExamRegistration existing = new ExamRegistration();
        existing.setSession(session);
        existing.setTenantId(tenantId);
        existing.setStudentPublicId(studentPublicId);
        existing.setStatus(AudienceMemberStatus.EXCLUDED);
        existing.setSourceSummary("stale source");

        when(sessionRepository.findByPublicIdAndTenantId(sessionPublicId, tenantId)).thenReturn(Optional.of(session));
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(20L)).thenReturn(List.of(source));
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId))
                .thenReturn(List.of(studentPublicId));
        when(registrationRepository.findEnrollmentsOfStudents(tenantId, List.of(studentPublicId)))
                .thenReturn(List.of());
        var firstPreview = audienceService.previewAudience(sessionPublicId, hostAdmin);
        var secondPreview = audienceService.previewAudience(sessionPublicId, hostAdmin);

        assertThat(firstPreview.members()).extracting(AudienceMemberResponse::status)
                .containsExactly(AudienceMemberStatus.ELIGIBLE);
        assertThat(secondPreview.members()).extracting(AudienceMemberResponse::status)
                .containsExactly(AudienceMemberStatus.ELIGIBLE);
        assertThat(existing.getStatus()).isEqualTo(AudienceMemberStatus.EXCLUDED);
        verify(registrationRepository, never()).saveAll(any());
        verify(registrationRepository, never()).deleteBySessionId(20L);
    }

    @Test
    void publish_reconcilesEnrollmentResponseBeforeScheduling() {
        ExamSession session = readyPublishSession();
        UUID enrolledStudentId = UUID.randomUUID();
        UUID conflictingStudentId = UUID.randomUUID();
        ExamRegistration enrolledMember = audienceMember(session, enrolledStudentId);
        ExamRegistration conflictingMember = audienceMember(session, conflictingStudentId);
        List<ExamRegistration> audience = List.of(enrolledMember, conflictingMember);
        stubPublishDependencies(session, audience);
        SlotConflict conflict = new SlotConflict(conflictingStudentId, UUID.randomUUID(), "Morning exam");
        when(enrollmentService.bulkEnrollPublishedExam(any(), any())).thenReturn(
                new BulkEnrollResponse(List.of(enrolledStudentId), List.of(), List.of(conflict)));

        var response = publishService.publish(session.getPublicId(), hostAdmin);

        assertThat(response.status()).isEqualTo(SessionStatus.SCHEDULED.name());
        assertThat(enrolledMember.getStatus()).isEqualTo(AudienceMemberStatus.ENROLLED);
        assertThat(enrolledMember.getPublishedAt()).isNotNull();
        assertThat(conflictingMember.getStatus()).isEqualTo(AudienceMemberStatus.EXCLUDED);
        assertThat(conflictingMember.getReason()).isEqualTo(AudienceDecisionReason.SCHEDULE_OVERLAP);
        assertThat(conflictingMember.getPriorSessionPublicId()).isEqualTo(conflict.conflictingSessionPublicId());
        assertThat(conflictingMember.getPriorSessionName()).isEqualTo(conflict.conflictingSessionName());
        verify(registrationRepository).saveAll(audience);
        verify(sessionRepository).flush();
        verify(auditLogService).record(hostAdmin, SessionConstants.AGGREGATE_EXAM_SESSION,
                session.getPublicId().toString(), session.getName(), session.getSessionCode(),
                SessionConstants.EVENT_EXAM_PUBLISHED, session.getName());
    }

    @Test
    void publish_allConflictsKeepsSessionReadyAndReturnsConflict() {
        ExamSession session = readyPublishSession();
        UUID studentId = UUID.randomUUID();
        ExamRegistration member = audienceMember(session, studentId);
        List<ExamRegistration> audience = List.of(member);
        stubPublishDependencies(session, audience);
        SlotConflict conflict = new SlotConflict(studentId, UUID.randomUUID(), "Existing exam");
        when(enrollmentService.bulkEnrollPublishedExam(any(), any())).thenReturn(
                new BulkEnrollResponse(List.of(), List.of(), List.of(conflict)));

        assertThatThrownBy(() -> publishService.publish(session.getPublicId(), hostAdmin))
                .isInstanceOf(ExamAudienceConflictException.class)
                .satisfies(error -> assertThat(((DomainException) error).getData())
                        .isEqualTo(List.of(conflict)));

        assertThat(session.getStatus()).isEqualTo(SessionStatus.READY);
        assertThat(member.getStatus()).isEqualTo(AudienceMemberStatus.EXCLUDED);
        assertThat(member.getReason()).isEqualTo(AudienceDecisionReason.SCHEDULE_OVERLAP);
        verify(registrationRepository).flush();
        verify(sessionRepository, never()).flush();
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void addSource_rejectsStudentAlreadyCoveredByAddedClass() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        session.setId(21L);
        UUID classPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        ExamSessionAudienceSource classSource = new ExamSessionAudienceSource();
        classSource.setSession(session);
        classSource.setTenantId(tenantId);
        classSource.setSourceType(AudienceSourceType.CLASS);
        classSource.setSourcePublicId(classPublicId);
        stubOwnedSession(session);
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(21L)).thenReturn(List.of(classSource));
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId))
                .thenReturn(List.of(studentPublicId));

        assertThatThrownBy(() -> audienceService.addSource(session.getPublicId(),
                new AudienceSourceRequest(AudienceSourceType.STUDENT, studentPublicId), hostAdmin))
                .isInstanceOf(AudienceSourceAlreadyCoveredException.class);

        verify(sourceRepository, never()).save(any());
    }

    @Test
    void addSource_allowsStudentOutsideAddedClass() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        session.setId(22L);
        UUID classPublicId = UUID.randomUUID();
        UUID studentPublicId = UUID.randomUUID();
        ExamSessionAudienceSource classSource = new ExamSessionAudienceSource();
        classSource.setSession(session);
        classSource.setTenantId(tenantId);
        classSource.setSourceType(AudienceSourceType.CLASS);
        classSource.setSourcePublicId(classPublicId);
        stubOwnedSession(session);
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(22L)).thenReturn(List.of(classSource));
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId))
                .thenReturn(List.of(UUID.randomUUID()));
        when(sourceRepository.save(any(ExamSessionAudienceSource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        audienceService.addSource(session.getPublicId(),
                new AudienceSourceRequest(AudienceSourceType.STUDENT, studentPublicId), hostAdmin);

        verify(sourceRepository).save(any(ExamSessionAudienceSource.class));
    }

    @Test
    void addSource_rejectsClassOfAlreadyAddedProgram() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        session.setId(23L);
        UUID programPublicId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        ExamSessionAudienceSource programSource = new ExamSessionAudienceSource();
        programSource.setSession(session);
        programSource.setTenantId(tenantId);
        programSource.setSourceType(AudienceSourceType.PROGRAM);
        programSource.setSourcePublicId(programPublicId);
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId)).thenReturn(List.of());
        when(enrollmentModuleService.findProgramPublicIdOfClass(tenantId, classPublicId)).thenReturn(programPublicId);
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(23L)).thenReturn(List.of(programSource));

        assertThatThrownBy(() -> audienceService.addSource(session.getPublicId(),
                new AudienceSourceRequest(AudienceSourceType.CLASS, classPublicId), hostAdmin))
                .isInstanceOf(AudienceSourceAlreadyCoveredException.class);

        verify(sourceRepository, never()).save(any());
    }

    @Test
    void addSource_allowsProgramEvenWhenOneOfItsClassesIsAlreadyAdded() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        session.setId(24L);
        UUID programPublicId = UUID.randomUUID();
        ExamSessionAudienceSource classSource = new ExamSessionAudienceSource();
        classSource.setSession(session);
        classSource.setTenantId(tenantId);
        classSource.setSourceType(AudienceSourceType.CLASS);
        classSource.setSourcePublicId(UUID.randomUUID());
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);
        when(enrollmentModuleService.findActiveStudentPublicIdsByProgram(tenantId, programPublicId))
                .thenReturn(List.of());
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(24L)).thenReturn(List.of(classSource));
        when(sourceRepository.save(any(ExamSessionAudienceSource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        audienceService.addSource(session.getPublicId(),
                new AudienceSourceRequest(AudienceSourceType.PROGRAM, programPublicId), hostAdmin);

        verify(sourceRepository).save(any(ExamSessionAudienceSource.class));
    }

    @Test
    void addSources_savesEachNewStudentOnceAndAuditsOnce() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        session.setId(25L);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        stubOwnedSession(session);
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(25L)).thenReturn(List.of());
        when(sourceRepository.save(any(ExamSessionAudienceSource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<AudienceSourceResponse> responses = audienceService.addSources(session.getPublicId(), List.of(
                new AudienceSourceRequest(AudienceSourceType.STUDENT, first),
                new AudienceSourceRequest(AudienceSourceType.STUDENT, second),
                new AudienceSourceRequest(AudienceSourceType.STUDENT, first)), hostAdmin);

        assertThat(responses).extracting(AudienceSourceResponse::sourcePublicId).containsExactly(first, second);
        verify(sourceRepository, times(2)).save(any(ExamSessionAudienceSource.class));
        verify(auditLogService).record(hostAdmin, SessionConstants.AGGREGATE_EXAM_SESSION,
                session.getPublicId().toString(), session.getName(), session.getSessionCode(),
                SessionConstants.EVENT_EXAM_AUDIENCE_SOURCE_ADDED, "2 sources");
    }

    @Test
    void addSources_isIdempotentForSourcesAlreadyOnSession() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        session.setId(26L);
        UUID studentPublicId = UUID.randomUUID();
        ExamSessionAudienceSource existing = new ExamSessionAudienceSource();
        existing.setSession(session);
        existing.setTenantId(tenantId);
        existing.setSourceType(AudienceSourceType.STUDENT);
        existing.setSourcePublicId(studentPublicId);
        stubOwnedSession(session);
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(26L)).thenReturn(List.of(existing));

        List<AudienceSourceResponse> responses = audienceService.addSources(session.getPublicId(),
                List.of(new AudienceSourceRequest(AudienceSourceType.STUDENT, studentPublicId)), hostAdmin);

        assertThat(responses).hasSize(1);
        verify(sourceRepository, never()).save(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void publish_alreadyEnrolledCountsAsSuccessfulReconciliation() {
        ExamSession session = readyPublishSession();
        UUID studentId = UUID.randomUUID();
        ExamRegistration member = audienceMember(session, studentId);
        List<ExamRegistration> audience = List.of(member);
        stubPublishDependencies(session, audience);
        when(enrollmentService.bulkEnrollPublishedExam(any(), any())).thenReturn(
                new BulkEnrollResponse(List.of(), List.of(studentId), List.of()));

        publishService.publish(session.getPublicId(), hostAdmin);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(member.getStatus()).isEqualTo(AudienceMemberStatus.ENROLLED);
    }

    @Test
    void generate_buildsOneFrozenExamAndMovesTheDraftToReady() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        ExamRegistration member = audienceMember(session, UUID.randomUUID());
        stubPublishDependencies(session, List.of(member));
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);
        UUID examVersionId = UUID.randomUUID();
        var snapshot = new com.pte.assessment.dto.response.SnapshotResponse(examVersionId, "Draft", 1,
                UUID.randomUUID(), templateId, 3, tenantId, List.of());
        when(assessmentService.generateDeterministic(eq(session.getName()), eq(templateId), anyLong(), eq(hostAdmin),
                any())).thenReturn(snapshot);

        var response = publishService.generate(session.getPublicId(), "key-1", hostAdmin);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.READY);
        assertThat(session.getExamVersionPublicId()).isEqualTo(examVersionId);
        assertThat(response.publicId()).isEqualTo(examVersionId);
        assertThat(response.formsTotal()).isEqualTo(1);
        verify(auditLogService).record(hostAdmin, SessionConstants.AGGREGATE_EXAM_SESSION,
                session.getPublicId().toString(), session.getName(), session.getSessionCode(),
                SessionConstants.EVENT_EXAM_GENERATED, session.getName());
    }

    @Test
    void generate_onAReadySession_replaysWithoutBuildingAnotherExam() {
        ExamSession session = readyPublishSession();
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);

        var response = publishService.generate(session.getPublicId(), "key-2", hostAdmin);

        assertThat(response.publicId()).isEqualTo(session.getExamVersionPublicId());
        verifyNoInteractions(assessmentService);
    }

    @Test
    void generate_requiresAnIdempotencyKey() {
        assertThatThrownBy(() -> publishService.generate(UUID.randomUUID(), "  ", hostAdmin))
                .isInstanceOf(ExamDraftConfigurationException.class);
        verifyNoInteractions(assessmentService);
    }

    @Test
    void generation_beforeGenerateIsNotReady() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        when(sessionRepository.findByPublicIdAndTenantId(session.getPublicId(), tenantId))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> publishService.generation(session.getPublicId(), hostAdmin))
                .isInstanceOf(com.pte.session.internal.exception.GenerationNotReadyException.class);
    }

    private ExamSession readyPublishSession() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        session.setStatus(SessionStatus.READY);
        session.setExamVersionPublicId(UUID.randomUUID());
        session.setExamVersionPublicId(UUID.randomUUID());
        return session;
    }

    private ExamRegistration audienceMember(ExamSession session, UUID studentPublicId) {
        ExamRegistration member = new ExamRegistration();
        member.setSession(session);
        member.setTenantId(tenantId);
        member.setLicenseKey("LIC-TEST");
        member.setStudentPublicId(studentPublicId);
        member.setStatus(AudienceMemberStatus.ELIGIBLE);
        return member;
    }

    private void stubPublishDependencies(ExamSession session, List<ExamRegistration> audience) {
        List<UUID> studentIds = audience.stream().map(ExamRegistration::getStudentPublicId).toList();
        List<ExamSessionAudienceSource> sources = studentIds.stream()
                .map(studentId -> {
                    ExamSessionAudienceSource source = new ExamSessionAudienceSource();
                    source.setSession(session);
                    source.setTenantId(tenantId);
                    source.setSourceType(AudienceSourceType.STUDENT);
                    source.setSourcePublicId(studentId);
                    return source;
                }).toList();
        SubscriptionView subscription = new SubscriptionView(subscriptionId, tenantId, UUID.randomUUID(),
                "LIC-TEST", opensAt.minusSeconds(60), closesAt.plusSeconds(3600), 100,
                SubscriptionStatus.ACTIVE, ActivationSource.LICENSE_CODE);
        // generate and publish read the session through different lookups, so these are shared leniently.
        org.mockito.Mockito.lenient().when(sessionRepository.findByPublicIdAndTenantId(session.getPublicId(), tenantId))
                .thenReturn(Optional.of(session));
        org.mockito.Mockito.lenient().when(sessionRepository.findWithLockByPublicIdAndTenantId(session.getPublicId(), tenantId))
                .thenReturn(Optional.of(session));
        org.mockito.Mockito.lenient().when(billingService.lockSubscriptions(List.of(subscriptionId), tenantId))
                .thenReturn(List.of(subscription));
        when(billingService.getActiveSubscription(subscriptionId, tenantId))
                .thenReturn(Optional.of(subscription));
        when(assessmentService.getTemplateFeasibility(session.getExamDefinitionPublicId(), session.getSelectedSkills()))
                .thenReturn(new com.pte.assessment.dto.response.ExamDefinitionFeasibilityResponse(
                        templateId, 3, true, List.of()));
        when(sessionRepository.findFirstOverlapping(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(sources);
        when(registrationRepository.findEnrollmentsOfStudents(tenantId, studentIds))
                .thenReturn(List.of());
        when(registrationRepository.findBySessionIdOrderByStudentPublicIdAsc(session.getId())).thenReturn(audience);
    }

    @Test
    void addSources_failsWholeBatchWhenAnySourceIsAlreadyCovered() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        session.setId(27L);
        UUID classPublicId = UUID.randomUUID();
        UUID covered = UUID.randomUUID();
        UUID outside = UUID.randomUUID();
        ExamSessionAudienceSource classSource = new ExamSessionAudienceSource();
        classSource.setSession(session);
        classSource.setTenantId(tenantId);
        classSource.setSourceType(AudienceSourceType.CLASS);
        classSource.setSourcePublicId(classPublicId);
        stubOwnedSession(session);
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(27L)).thenReturn(List.of(classSource));
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId))
                .thenReturn(List.of(covered));
        when(sourceRepository.save(any(ExamSessionAudienceSource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> audienceService.addSources(session.getPublicId(), List.of(
                new AudienceSourceRequest(AudienceSourceType.STUDENT, outside),
                new AudienceSourceRequest(AudienceSourceType.STUDENT, covered)), hostAdmin))
                .isInstanceOf(AudienceSourceAlreadyCoveredException.class);

        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    private void stubOwnedSession(ExamSession session) {
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);
    }

    private void stubTemplateAndSubscription() {
        when(examDefinitionService.findActiveByPublicId(templateId)).thenReturn(Optional.of(
                templateResponse()));
        when(billingService.getActiveSubscription(subscriptionId, tenantId)).thenReturn(Optional.of(
                new SubscriptionView(subscriptionId, tenantId, UUID.randomUUID(), "LIC-TEST",
                        Instant.now().minusSeconds(60), Instant.now().plusSeconds(86400), 100,
                        SubscriptionStatus.ACTIVE, ActivationSource.LICENSE_CODE)));
    }

    private ExamDefinitionResponse templateResponse() {
        return new ExamDefinitionResponse(templateId, "PTE", 3, "PTE template", "ACTIVE", List.of(
                new ExamDefinitionItemResponse("READ_ALOUD", "SPEAKING", 1, 1, 1, 10, 30,
                        "OBJECTIVE", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO)));
    }

    private ExamDefinitionResponse twoSkillTemplateResponse() {
        return new ExamDefinitionResponse(templateId, "PTE", 3, "PTE template", "ACTIVE", List.of(
                new ExamDefinitionItemResponse("READ_ALOUD", "SPEAKING", 1, 1, 1, 10, 30,
                        "OBJECTIVE", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO),
                new ExamDefinitionItemResponse("ESSAY", "WRITING", 2, 1, 1, 0, 1200,
                        "SUBJECTIVE", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO)));
    }

    private void stubSubscriptionForDraft() {
        when(billingService.getActiveSubscription(subscriptionId, tenantId)).thenReturn(Optional.of(
                new SubscriptionView(subscriptionId, tenantId, UUID.randomUUID(), "LIC-TEST",
                        Instant.now().minusSeconds(60), Instant.now().plusSeconds(86400), 100,
                        SubscriptionStatus.ACTIVE, ActivationSource.LICENSE_CODE)));
    }

    private ExamSession draftSession(ExamMode mode, com.pte.session.domain.enums.LockdownMode lockdownMode) {
        ExamSession session = new ExamSession();
        session.setId(13L);
        session.setPublicId(UUID.randomUUID());
        session.setTenantId(tenantId);
        session.setSubscriptionId(subscriptionId);
        session.setExamDefinitionPublicId(templateId);
        session.setExamDefinitionVersion(3);
        session.setName("Draft");
        session.setStatus(SessionStatus.DRAFT);
        session.setDraftVersion(0L);
        session.setExamMode(mode);
        session.setReusePolicy(ReusePolicy.ALLOW);
        session.setSelectedSkills(new java.util.LinkedHashSet<>(List.of("SPEAKING")));
        session.setMaxRetriesPerStudent(0);
        session.setOpensAt(opensAt);
        session.setClosesAt(closesAt);
        session.setCapacity(20);
        ExamPolicy policy = ExamPolicy.realExamDefault();
        policy.setLockdownMode(lockdownMode);
        session.setPolicy(policy);
        return session;
    }
}
