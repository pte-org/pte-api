package com.pte.session.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.billing.BillingService;
import com.pte.billing.SubscriptionView;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.enrollment.EnrollmentModuleService;
import com.pte.identity.IdentityService;
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
                billingService, enrollmentModuleService, identityService, startedAttemptLookup, auditLogService);
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
        when(sessionRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            var session = invocation.getArgument(0, com.pte.session.domain.ExamSession.class);
            session.setPublicId(UUID.randomUUID());
            session.setId(10L);
            return session;
        });

        var response = service.createDraft(new CreateExamDraftRequest(
                "  HK1 mock  ", templateId, subscriptionId, opensAt, closesAt,
                ExamMode.MOCK_TEST, null, null, " HK1-2026 ", 50), hostAdmin);

        assertThat(response.status()).isEqualTo(SessionStatus.DRAFT.name());
        assertThat(response.formMode()).isEqualTo(FormMode.UNIQUE_FORM_PER_STUDENT);
        assertThat(response.reusePolicy()).isEqualTo(ReusePolicy.EXCLUDE_STARTED_IN_SERIES);
        assertThat(response.seriesKey()).isEqualTo("HK1-2026");
        assertThat(response.templatePublicId()).isEqualTo(templateId);
        assertThat(response.templateVersion()).isEqualTo(3);
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
    }

    @Test
    void createDraft_rejectsOfficialSharedFormBeforePersistence() {
        stubTemplateAndSubscription();

        assertThatThrownBy(() -> service.createDraft(new CreateExamDraftRequest(
                "Unsafe mock", templateId, subscriptionId, opensAt, closesAt,
                ExamMode.MOCK_TEST, FormMode.SHARED_FORM, ReusePolicy.ALLOW, null, 20), hostAdmin))
                .isInstanceOf(ExamDraftConfigurationException.class)
                .extracting(throwable -> ((DomainException) throwable).getUserMessage())
                .isEqualTo(SessionConstants.EXAM_OFFICIAL_UNIQUE_FORM_REQUIRED);

        verify(sessionRepository, never()).saveAndFlush(any());
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

    private void stubTemplateAndSubscription() {
        when(scoreTemplateService.findActiveByPublicId(templateId)).thenReturn(Optional.of(
                new ScoreTemplateResponse(templateId, "PTE", 3, "PTE template", "ACTIVE", List.of())));
        when(billingService.getActiveSubscription(subscriptionId, tenantId)).thenReturn(Optional.of(
                new SubscriptionView(subscriptionId, tenantId, UUID.randomUUID(), "LIC-TEST",
                        Instant.now().minusSeconds(60), Instant.now().plusSeconds(86400), 100,
                        SubscriptionStatus.ACTIVE, ActivationSource.LICENSE_CODE)));
    }
}
