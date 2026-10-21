package com.pte.session.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.billing.BillingService;
import com.pte.billing.SubscriptionView;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.enrollment.EnrollmentModuleService;
import com.pte.identity.IdentityService;
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.session.domain.ExamPolicy;
import com.pte.session.domain.ExamAudienceMember;
import com.pte.session.domain.ExamAudienceSource;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ExamGenerationJob;
import com.pte.session.domain.enums.AudienceDecisionReason;
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.session.domain.enums.AudienceSourceType;
import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.FormMode;
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
import com.pte.session.internal.repository.ExamAudienceMemberRepository;
import com.pte.session.internal.repository.ExamAudienceSourceRepository;
import com.pte.session.internal.repository.ExamFormRepository;
import com.pte.session.internal.repository.ExamGenerationJobRepository;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.session.internal.repository.EnrollmentRepository;
import com.pte.session.internal.repository.FormAssignmentRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamOrchestrationServiceTest {

    @Mock
    private ExamSessionRepository sessionRepository;
    @Mock
    private ExamAudienceSourceRepository sourceRepository;
    @Mock
    private ExamAudienceMemberRepository memberRepository;
    @Mock
    private ExamGenerationJobRepository jobRepository;
    @Mock
    private ExamFormRepository formRepository;
    @Mock
    private FormAssignmentRepository formAssignmentRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private SessionLifecycleService sessionLifecycleService;
    @Mock
    private EnrollmentService enrollmentService;
    @Mock
    private AssessmentService assessmentService;
    @Mock
    private ScoreTemplateService scoreTemplateService;
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

    private ExamOrchestrationService service;
    private UUID tenantId;
    private UUID templateId;
    private UUID subscriptionId;
    private CurrentUser hostAdmin;
    private Instant opensAt;
    private Instant closesAt;

    @BeforeEach
    void setUp() {
        service = new ExamOrchestrationService(sessionRepository, sourceRepository, memberRepository,
                jobRepository, formRepository, formAssignmentRepository, enrollmentRepository,
                sessionLifecycleService, enrollmentService, assessmentService, scoreTemplateService,
                billingService, enrollmentModuleService, identityService, startedAttemptLookup, auditLogService,
                sessionCodeGenerator, eventPublisher, tenancyService);
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

        assertThatThrownBy(() -> service.createDraft(new CreateExamDraftRequest(
                "HK1 mock", templateId, subscriptionId, opensAt, closesAt,
                ExamMode.OFFICIAL_EXAM, null, null, null, 50), hostAdmin))
                .isInstanceOf(TenantSuspendedException.class);

        verifyNoInteractions(scoreTemplateService, billingService, sessionRepository);
    }

    @Test
    void createDraft_defaultsOfficialExamToUniqueFormAndStartedSeriesExclusion() {
        stubTemplateAndSubscription();
        when(sessionCodeGenerator.generate(tenantId, opensAt)).thenReturn("FPT-261010-K7QM");
        when(sessionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            var session = invocation.getArgument(0, com.pte.session.domain.ExamSession.class);
            session.setPublicId(UUID.randomUUID());
            session.setId(10L);
            return session;
        });

        var response = service.createDraft(new CreateExamDraftRequest(
                "  HK1 mock  ", templateId, subscriptionId, opensAt, closesAt,
                ExamMode.OFFICIAL_EXAM, null, null, " HK1-2026 ", 50), hostAdmin);

        assertThat(response.status()).isEqualTo(SessionStatus.DRAFT.name());
        assertThat(response.formMode()).isEqualTo(FormMode.UNIQUE_FORM_PER_STUDENT);
        assertThat(response.reusePolicy()).isEqualTo(ReusePolicy.EXCLUDE_STARTED_IN_SERIES);
        assertThat(response.seriesKey()).isEqualTo("HK1-2026");
        assertThat(response.templatePublicId()).isEqualTo(templateId);
        assertThat(response.templateVersion()).isEqualTo(3);
        assertThat(response.policy().lockdownMode()).isEqualTo("STRICT");
        assertThat(response.sessionCode()).isEqualTo("FPT-261010-K7QM");
        verify(auditLogService).record(hostAdmin, SessionConstants.AGGREGATE_EXAM_SESSION,
                response.publicId().toString(), "HK1 mock", "FPT-261010-K7QM",
                SessionConstants.EVENT_EXAM_DRAFT_CREATED, "HK1 mock");
    }

    @Test
    void createDraft_partialSkillSelection_isRejected() {
        when(scoreTemplateService.findActiveByPublicId(templateId)).thenReturn(Optional.of(
                twoSkillTemplateResponse()));
        stubSubscriptionForDraft();

        assertThatThrownBy(() -> service.createDraft(new CreateExamDraftRequest(
                "Speaking only", templateId, subscriptionId, opensAt, closesAt,
                ExamMode.OFFICIAL_EXAM, null, null, "SERIES", 50, List.of("SPEAKING"), null), hostAdmin))
                .isInstanceOf(ExamDraftConfigurationException.class)
                .extracting(ex -> ((DomainException) ex).getUserMessage())
                .isEqualTo(SessionConstants.SKILLS_FULL_TEMPLATE_REQUIRED);

        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createDraft_officialWithNonStrictLockdown_isRejected() {
        stubTemplateAndSubscription();

        assertThatThrownBy(() -> service.createDraft(new CreateExamDraftRequest(
                "Invalid official", templateId, subscriptionId, opensAt, closesAt,
                ExamMode.OFFICIAL_EXAM, null, null, "SERIES", 50, null, null,
                com.pte.session.domain.enums.LockdownMode.STANDARD), hostAdmin))
                .isInstanceOf(com.pte.session.internal.exception.InvalidLockdownModeException.class);

        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void createDraft_withRetries_isRejectedBecauseEveryStudentSitsOnce() {
        stubTemplateAndSubscription();

        assertThatThrownBy(() -> service.createDraft(new CreateExamDraftRequest(
                "Retries", templateId, subscriptionId, opensAt, closesAt,
                ExamMode.OFFICIAL_EXAM, null, null, "SERIES", 50, null, 2,
                com.pte.session.domain.enums.LockdownMode.STRICT), hostAdmin))
                .isInstanceOf(com.pte.session.internal.exception.ExamDraftConfigurationException.class);

        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateDraft_withoutLockdown_preservesStrictAfterRetryPatch() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);
        when(scoreTemplateService.getByPublicId(templateId)).thenReturn(templateResponse());
        stubSubscriptionForDraft();
        when(sessionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateDraft(session.getPublicId(), new PatchExamDraftRequest(
                "Renamed", null, null, null, null, null, null, null, null, null,
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

        assertThatThrownBy(() -> service.updateDraft(session.getPublicId(), new PatchExamDraftRequest(
                null, null, null, null, null, null, null, null, null, null,
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
        when(scoreTemplateService.getByPublicId(templateId)).thenReturn(templateResponse());
        stubSubscriptionForDraft();
        when(sessionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateDraft(session.getPublicId(), new PatchExamDraftRequest(
                null, null, null, null, null, ExamMode.OFFICIAL_EXAM, null, null, null, null,
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
        when(scoreTemplateService.getByPublicId(templateId)).thenReturn(templateResponse());
        stubSubscriptionForDraft();
        when(sessionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateDraft(session.getPublicId(), new PatchExamDraftRequest(
                null, null, null, opensAt.plusSeconds(1800), closesAt.plusSeconds(1800), null, null, null, null,
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

        assertThatThrownBy(() -> service.updateDraft(session.getPublicId(),
                new PatchExamDraftRequest("Renamed", null, null, null, null, null, null, null, null,
                        null, 6L), hostAdmin))
                .isInstanceOf(ExamDraftVersionConflictException.class);

        verify(sessionRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteDraft_softDeletesDraftAndRecordsAuditEvent() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);

        service.deleteDraft(session.getPublicId(), hostAdmin);

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

        assertThatThrownBy(() -> service.deleteDraft(session.getPublicId(), hostAdmin))
                .isInstanceOf(ExamDraftNotDeletableException.class);

        assertThat(session.isDeleted()).isFalse();
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void cancelDraft_isRejectedBecauseDraftUsesDelete() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);

        assertThatThrownBy(() -> service.cancel(session.getPublicId(), hostAdmin))
                .isInstanceOf(com.pte.session.internal.exception.SessionNotCancellableException.class);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void cancelPreparing_cancelsActiveGenerationAndReturnsToDraft() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        session.setStatus(SessionStatus.PREPARING);
        ExamGenerationJob job = new ExamGenerationJob();
        job.setSession(session);
        job.setStatus(com.pte.session.domain.enums.GenerationJobStatus.RUNNING);
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);
        when(jobRepository.findFirstBySessionIdOrderByCreatedAtDesc(session.getId())).thenReturn(Optional.of(job));

        var response = service.cancel(session.getPublicId(), hostAdmin);

        assertThat(response.status()).isEqualTo(SessionStatus.DRAFT.name());
        assertThat(job.getStatus()).isEqualTo(com.pte.session.domain.enums.GenerationJobStatus.CANCELLED);
        verify(eventPublisher, never()).publishEvent(any());
        verify(auditLogService).record(hostAdmin, SessionConstants.AGGREGATE_EXAM_SESSION,
                session.getPublicId().toString(), session.getName(), session.getSessionCode(),
                SessionConstants.EVENT_EXAM_GENERATION_CANCELLED,
                "Draft (PREPARING -> DRAFT)");
    }

    @Test
    void cancelScheduled_publishesNeutralCancellationEvent() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        session.setStatus(SessionStatus.SCHEDULED);
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);

        var response = service.cancel(session.getPublicId(), hostAdmin);

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

        ExamAudienceSource source = new ExamAudienceSource();
        source.setSession(session);
        source.setTenantId(tenantId);
        source.setSourceType(AudienceSourceType.CLASS);
        source.setSourcePublicId(classPublicId);

        ExamAudienceMember existing = new ExamAudienceMember();
        existing.setSession(session);
        existing.setTenantId(tenantId);
        existing.setStudentPublicId(studentPublicId);
        existing.setStatus(AudienceMemberStatus.EXCLUDED);
        existing.setSourceSummary("stale source");

        when(sessionRepository.findByPublicIdAndTenantId(sessionPublicId, tenantId)).thenReturn(Optional.of(session));
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(20L)).thenReturn(List.of(source));
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId))
                .thenReturn(List.of(studentPublicId));
        when(enrollmentRepository.findByTenantIdAndStudentPublicIdIn(tenantId, List.of(studentPublicId)))
                .thenReturn(List.of());
        var firstPreview = service.previewAudience(sessionPublicId, hostAdmin);
        var secondPreview = service.previewAudience(sessionPublicId, hostAdmin);

        assertThat(firstPreview.members()).extracting(AudienceMemberResponse::status)
                .containsExactly(AudienceMemberStatus.ELIGIBLE);
        assertThat(secondPreview.members()).extracting(AudienceMemberResponse::status)
                .containsExactly(AudienceMemberStatus.ELIGIBLE);
        assertThat(existing.getStatus()).isEqualTo(AudienceMemberStatus.EXCLUDED);
        verify(memberRepository, never()).saveAll(any());
        verify(memberRepository, never()).deleteBySessionId(20L);
    }

    @Test
    void publish_reconcilesEnrollmentResponseBeforeScheduling() {
        ExamSession session = readyPublishSession();
        UUID enrolledStudentId = UUID.randomUUID();
        UUID conflictingStudentId = UUID.randomUUID();
        ExamAudienceMember enrolledMember = audienceMember(session, enrolledStudentId);
        ExamAudienceMember conflictingMember = audienceMember(session, conflictingStudentId);
        List<ExamAudienceMember> audience = List.of(enrolledMember, conflictingMember);
        stubPublishDependencies(session, audience);
        SlotConflict conflict = new SlotConflict(conflictingStudentId, UUID.randomUUID(), "Morning exam");
        when(enrollmentService.bulkEnrollPublishedExam(any(), any(), any())).thenReturn(
                new BulkEnrollResponse(List.of(enrolledStudentId), List.of(), List.of(conflict)));

        var response = service.publish(session.getPublicId(), hostAdmin);

        assertThat(response.status()).isEqualTo(SessionStatus.SCHEDULED.name());
        assertThat(enrolledMember.getStatus()).isEqualTo(AudienceMemberStatus.ENROLLED);
        assertThat(enrolledMember.getPublishedAt()).isNotNull();
        assertThat(conflictingMember.getStatus()).isEqualTo(AudienceMemberStatus.EXCLUDED);
        assertThat(conflictingMember.getReason()).isEqualTo(AudienceDecisionReason.SCHEDULE_OVERLAP);
        assertThat(conflictingMember.getPriorSessionPublicId()).isEqualTo(conflict.conflictingSessionPublicId());
        assertThat(conflictingMember.getPriorSessionName()).isEqualTo(conflict.conflictingSessionName());
        verify(memberRepository).saveAll(audience);
        verify(sessionRepository).flush();
        verify(auditLogService).record(hostAdmin, SessionConstants.AGGREGATE_EXAM_SESSION,
                session.getPublicId().toString(), session.getName(), session.getSessionCode(),
                SessionConstants.EVENT_EXAM_PUBLISHED, session.getName());
    }

    @Test
    void publish_allConflictsKeepsSessionReadyAndReturnsConflict() {
        ExamSession session = readyPublishSession();
        UUID studentId = UUID.randomUUID();
        ExamAudienceMember member = audienceMember(session, studentId);
        List<ExamAudienceMember> audience = List.of(member);
        stubPublishDependencies(session, audience);
        SlotConflict conflict = new SlotConflict(studentId, UUID.randomUUID(), "Existing exam");
        when(enrollmentService.bulkEnrollPublishedExam(any(), any(), any())).thenReturn(
                new BulkEnrollResponse(List.of(), List.of(), List.of(conflict)));

        assertThatThrownBy(() -> service.publish(session.getPublicId(), hostAdmin))
                .isInstanceOf(ExamAudienceConflictException.class)
                .satisfies(error -> assertThat(((DomainException) error).getData())
                        .isEqualTo(List.of(conflict)));

        assertThat(session.getStatus()).isEqualTo(SessionStatus.READY);
        assertThat(member.getStatus()).isEqualTo(AudienceMemberStatus.EXCLUDED);
        assertThat(member.getReason()).isEqualTo(AudienceDecisionReason.SCHEDULE_OVERLAP);
        verify(memberRepository).flush();
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
        ExamAudienceSource classSource = new ExamAudienceSource();
        classSource.setSession(session);
        classSource.setTenantId(tenantId);
        classSource.setSourceType(AudienceSourceType.CLASS);
        classSource.setSourcePublicId(classPublicId);
        stubOwnedSession(session);
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(21L)).thenReturn(List.of(classSource));
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId))
                .thenReturn(List.of(studentPublicId));

        assertThatThrownBy(() -> service.addSource(session.getPublicId(),
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
        ExamAudienceSource classSource = new ExamAudienceSource();
        classSource.setSession(session);
        classSource.setTenantId(tenantId);
        classSource.setSourceType(AudienceSourceType.CLASS);
        classSource.setSourcePublicId(classPublicId);
        stubOwnedSession(session);
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(22L)).thenReturn(List.of(classSource));
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId))
                .thenReturn(List.of(UUID.randomUUID()));
        when(sourceRepository.save(any(ExamAudienceSource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.addSource(session.getPublicId(),
                new AudienceSourceRequest(AudienceSourceType.STUDENT, studentPublicId), hostAdmin);

        verify(sourceRepository).save(any(ExamAudienceSource.class));
    }

    @Test
    void addSource_rejectsClassOfAlreadyAddedProgram() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        session.setId(23L);
        UUID programPublicId = UUID.randomUUID();
        UUID classPublicId = UUID.randomUUID();
        ExamAudienceSource programSource = new ExamAudienceSource();
        programSource.setSession(session);
        programSource.setTenantId(tenantId);
        programSource.setSourceType(AudienceSourceType.PROGRAM);
        programSource.setSourcePublicId(programPublicId);
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId)).thenReturn(List.of());
        when(enrollmentModuleService.findProgramPublicIdOfClass(tenantId, classPublicId)).thenReturn(programPublicId);
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(23L)).thenReturn(List.of(programSource));

        assertThatThrownBy(() -> service.addSource(session.getPublicId(),
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
        ExamAudienceSource classSource = new ExamAudienceSource();
        classSource.setSession(session);
        classSource.setTenantId(tenantId);
        classSource.setSourceType(AudienceSourceType.CLASS);
        classSource.setSourcePublicId(UUID.randomUUID());
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);
        when(enrollmentModuleService.findActiveStudentPublicIdsByProgram(tenantId, programPublicId))
                .thenReturn(List.of());
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(24L)).thenReturn(List.of(classSource));
        when(sourceRepository.save(any(ExamAudienceSource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.addSource(session.getPublicId(),
                new AudienceSourceRequest(AudienceSourceType.PROGRAM, programPublicId), hostAdmin);

        verify(sourceRepository).save(any(ExamAudienceSource.class));
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
        when(sourceRepository.save(any(ExamAudienceSource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<AudienceSourceResponse> responses = service.addSources(session.getPublicId(), List.of(
                new AudienceSourceRequest(AudienceSourceType.STUDENT, first),
                new AudienceSourceRequest(AudienceSourceType.STUDENT, second),
                new AudienceSourceRequest(AudienceSourceType.STUDENT, first)), hostAdmin);

        assertThat(responses).extracting(AudienceSourceResponse::sourcePublicId).containsExactly(first, second);
        verify(sourceRepository, times(2)).save(any(ExamAudienceSource.class));
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
        ExamAudienceSource existing = new ExamAudienceSource();
        existing.setSession(session);
        existing.setTenantId(tenantId);
        existing.setSourceType(AudienceSourceType.STUDENT);
        existing.setSourcePublicId(studentPublicId);
        stubOwnedSession(session);
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(26L)).thenReturn(List.of(existing));

        List<AudienceSourceResponse> responses = service.addSources(session.getPublicId(),
                List.of(new AudienceSourceRequest(AudienceSourceType.STUDENT, studentPublicId)), hostAdmin);

        assertThat(responses).hasSize(1);
        verify(sourceRepository, never()).save(any());
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void publish_alreadyEnrolledCountsAsSuccessfulReconciliation() {
        ExamSession session = readyPublishSession();
        UUID studentId = UUID.randomUUID();
        ExamAudienceMember member = audienceMember(session, studentId);
        List<ExamAudienceMember> audience = List.of(member);
        stubPublishDependencies(session, audience);
        when(enrollmentService.bulkEnrollPublishedExam(any(), any(), any())).thenReturn(
                new BulkEnrollResponse(List.of(), List.of(studentId), List.of()));

        service.publish(session.getPublicId(), hostAdmin);

        assertThat(session.getStatus()).isEqualTo(SessionStatus.SCHEDULED);
        assertThat(member.getStatus()).isEqualTo(AudienceMemberStatus.ENROLLED);
    }

    private ExamSession readyPublishSession() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        session.setStatus(SessionStatus.READY);
        session.setSnapshotPublicId(UUID.randomUUID());
        session.setGenerationJobPublicId(UUID.randomUUID());
        return session;
    }

    private ExamAudienceMember audienceMember(ExamSession session, UUID studentPublicId) {
        ExamAudienceMember member = new ExamAudienceMember();
        member.setSession(session);
        member.setTenantId(tenantId);
        member.setStudentPublicId(studentPublicId);
        member.setStatus(AudienceMemberStatus.ELIGIBLE);
        return member;
    }

    private void stubPublishDependencies(ExamSession session, List<ExamAudienceMember> audience) {
        List<UUID> studentIds = audience.stream().map(ExamAudienceMember::getStudentPublicId).toList();
        List<ExamAudienceSource> sources = studentIds.stream()
                .map(studentId -> {
                    ExamAudienceSource source = new ExamAudienceSource();
                    source.setSession(session);
                    source.setTenantId(tenantId);
                    source.setSourceType(AudienceSourceType.STUDENT);
                    source.setSourcePublicId(studentId);
                    return source;
                }).toList();
        SubscriptionView subscription = new SubscriptionView(subscriptionId, tenantId, UUID.randomUUID(),
                "LIC-TEST", opensAt.minusSeconds(60), closesAt.plusSeconds(3600), 100,
                SubscriptionStatus.ACTIVE, ActivationSource.LICENSE_CODE);
        when(sessionRepository.findByPublicIdAndTenantId(session.getPublicId(), tenantId))
                .thenReturn(Optional.of(session));
        when(sessionRepository.findWithLockByPublicIdAndTenantId(session.getPublicId(), tenantId))
                .thenReturn(Optional.of(session));
        when(billingService.lockSubscriptions(List.of(subscriptionId), tenantId))
                .thenReturn(List.of(subscription));
        when(billingService.getActiveSubscription(subscriptionId, tenantId))
                .thenReturn(Optional.of(subscription));
        when(assessmentService.getTemplateFeasibility(session.getTemplatePublicId(), session.getSelectedSkills()))
                .thenReturn(new com.pte.scoretemplate.dto.response.ScoreTemplateFeasibilityResponse(
                        templateId, 3, true, List.of()));
        when(sessionRepository.findFirstOverlapping(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())).thenReturn(sources);
        when(enrollmentRepository.findByTenantIdAndStudentPublicIdIn(tenantId, studentIds))
                .thenReturn(List.of());
        when(memberRepository.findBySessionIdOrderByStudentPublicIdAsc(session.getId())).thenReturn(audience);
    }

    @Test
    void addSources_failsWholeBatchWhenAnySourceIsAlreadyCovered() {
        ExamSession session = draftSession(ExamMode.OFFICIAL_EXAM,
                com.pte.session.domain.enums.LockdownMode.STRICT);
        session.setId(27L);
        UUID classPublicId = UUID.randomUUID();
        UUID covered = UUID.randomUUID();
        UUID outside = UUID.randomUUID();
        ExamAudienceSource classSource = new ExamAudienceSource();
        classSource.setSession(session);
        classSource.setTenantId(tenantId);
        classSource.setSourceType(AudienceSourceType.CLASS);
        classSource.setSourcePublicId(classPublicId);
        stubOwnedSession(session);
        when(sourceRepository.findBySessionIdOrderByCreatedAtAsc(27L)).thenReturn(List.of(classSource));
        when(enrollmentModuleService.findActiveStudentPublicIds(tenantId, classPublicId))
                .thenReturn(List.of(covered));
        when(sourceRepository.save(any(ExamAudienceSource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatThrownBy(() -> service.addSources(session.getPublicId(), List.of(
                new AudienceSourceRequest(AudienceSourceType.STUDENT, outside),
                new AudienceSourceRequest(AudienceSourceType.STUDENT, covered)), hostAdmin))
                .isInstanceOf(AudienceSourceAlreadyCoveredException.class);

        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    private void stubOwnedSession(ExamSession session) {
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);
    }

    private void stubTemplateAndSubscription() {
        when(scoreTemplateService.findActiveByPublicId(templateId)).thenReturn(Optional.of(
                templateResponse()));
        when(billingService.getActiveSubscription(subscriptionId, tenantId)).thenReturn(Optional.of(
                new SubscriptionView(subscriptionId, tenantId, UUID.randomUUID(), "LIC-TEST",
                        Instant.now().minusSeconds(60), Instant.now().plusSeconds(86400), 100,
                        SubscriptionStatus.ACTIVE, ActivationSource.LICENSE_CODE)));
    }

    private ScoreTemplateResponse templateResponse() {
        return new ScoreTemplateResponse(templateId, "PTE", 3, "PTE template", "ACTIVE", List.of(
                new ScoreTemplateItemResponse("READ_ALOUD", "SPEAKING", 1, 1, 1, 10, 30,
                        "OBJECTIVE", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO)));
    }

    private ScoreTemplateResponse twoSkillTemplateResponse() {
        return new ScoreTemplateResponse(templateId, "PTE", 3, "PTE template", "ACTIVE", List.of(
                new ScoreTemplateItemResponse("READ_ALOUD", "SPEAKING", 1, 1, 1, 10, 30,
                        "OBJECTIVE", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO),
                new ScoreTemplateItemResponse("ESSAY", "WRITING", 2, 1, 1, 0, 1200,
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
        session.setTemplatePublicId(templateId);
        session.setTemplateVersion(3);
        session.setName("Draft");
        session.setStatus(SessionStatus.DRAFT);
        session.setDraftVersion(0L);
        session.setExamMode(mode);
        session.setFormMode(FormMode.SHARED_FORM);
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
