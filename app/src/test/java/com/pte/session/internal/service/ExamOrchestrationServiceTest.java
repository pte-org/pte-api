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
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.session.domain.enums.AudienceSourceType;
import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.FormMode;
import com.pte.session.domain.enums.ReusePolicy;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.dto.request.CreateExamDraftRequest;
import com.pte.session.internal.dto.request.PatchExamDraftRequest;
import com.pte.session.internal.exception.ExamDraftConfigurationException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
                sessionCodeGenerator);
        tenantId = UUID.randomUUID();
        templateId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        hostAdmin = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        opensAt = Instant.now().plusSeconds(3600);
        closesAt = opensAt.plusSeconds(3600);
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
                response.publicId().toString(), SessionConstants.EVENT_EXAM_DRAFT_CREATED, "HK1 mock");
    }

    @Test
    void createDraft_defaultsPracticeToSharedFormAndAllowReuse() {
        stubTemplateAndSubscription();
        when(sessionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            var session = invocation.getArgument(0, com.pte.session.domain.ExamSession.class);
            session.setPublicId(UUID.randomUUID());
            session.setId(11L);
            return session;
        });

        var response = service.createDraft(new CreateExamDraftRequest(
                "Practice", templateId, subscriptionId, opensAt, closesAt,
                ExamMode.PRACTICE, null, null, null, 20), hostAdmin);

        assertThat(response.formMode()).isEqualTo(FormMode.SHARED_FORM);
        assertThat(response.reusePolicy()).isEqualTo(ReusePolicy.ALLOW);
        assertThat(response.seriesKey()).isNull();
        assertThat(response.policy().lockdownMode()).isEqualTo("NONE");
    }

    @Test
    void createDraft_practiceWithStandardLockdown_persistsStandard() {
        stubTemplateAndSubscription();
        when(sessionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            var session = invocation.getArgument(0, ExamSession.class);
            session.setPublicId(UUID.randomUUID());
            session.setId(12L);
            return session;
        });

        var response = service.createDraft(new CreateExamDraftRequest(
                "Practice controlled", templateId, subscriptionId, opensAt, closesAt,
                ExamMode.PRACTICE, null, null, null, 20, null, null,
                com.pte.session.domain.enums.LockdownMode.STANDARD), hostAdmin);

        assertThat(response.policy().lockdownMode()).isEqualTo("STANDARD");
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
    void updateDraft_sameModeWithoutLockdown_preservesStandardAfterRetryPatch() {
        ExamSession session = draftSession(ExamMode.PRACTICE,
                com.pte.session.domain.enums.LockdownMode.STANDARD);
        when(sessionLifecycleService.findOwnedWithLock(session.getPublicId(), hostAdmin)).thenReturn(session);
        when(scoreTemplateService.getByPublicId(templateId)).thenReturn(templateResponse());
        stubSubscriptionForDraft();
        when(sessionRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updateDraft(session.getPublicId(), new PatchExamDraftRequest(
                "Renamed", null, null, null, null, null, null, null, null, null,
                0L, null, 2, null), hostAdmin);

        assertThat(session.getMaxRetriesPerStudent()).isEqualTo(2);
        assertThat(response.policy().lockdownMode()).isEqualTo("STANDARD");
    }

    @Test
    void updateDraft_modeChangeWithoutLockdown_derivesOfficialStrictDefault() {
        ExamSession session = draftSession(ExamMode.PRACTICE,
                com.pte.session.domain.enums.LockdownMode.STANDARD);
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
        ExamSession session = draftSession(ExamMode.PRACTICE,
                com.pte.session.domain.enums.LockdownMode.STANDARD);
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
    void previewAudience_reusesPersistedMembersWhenPreflightRunsRepeatedly() {
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
        when(memberRepository.findBySessionIdOrderByStudentPublicIdAsc(20L)).thenReturn(List.of(existing));

        service.previewAudience(sessionPublicId, hostAdmin);
        service.previewAudience(sessionPublicId, hostAdmin);

        assertThat(existing.getStatus()).isEqualTo(AudienceMemberStatus.ELIGIBLE);
        assertThat(existing.getSourceSummary()).isEqualTo("CLASS:" + classPublicId);
        verify(memberRepository, times(2)).saveAll(any());
        verify(memberRepository, never()).deleteBySessionId(20L);
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
        ExamPolicy policy = ExamPolicy.forMode(mode);
        policy.setLockdownMode(lockdownMode);
        session.setPolicy(policy);
        return session;
    }
}
