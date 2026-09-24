package com.pte.session.internal.service;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.billing.BillingService;
import com.pte.billing.SubscriptionView;
import com.pte.enrollment.EnrollmentModuleService;
import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.session.domain.ExamAudienceMember;
import com.pte.session.domain.ExamAudienceSource;
import com.pte.session.domain.ExamForm;
import com.pte.session.domain.ExamGenerationJob;
import com.pte.session.domain.ExamPolicy;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.FormAssignment;
import com.pte.session.domain.Enrollment;
import com.pte.session.domain.enums.AudienceDecisionReason;
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.session.domain.enums.AudienceSourceType;
import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.FormMode;
import com.pte.session.domain.enums.GenerationJobStatus;
import com.pte.session.domain.enums.ReusePolicy;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.dto.request.AudienceSourceRequest;
import com.pte.session.internal.dto.request.CreateExamDraftRequest;
import com.pte.session.internal.dto.request.PatchExamDraftRequest;
import com.pte.session.internal.dto.request.BulkEnrollRequest;
import com.pte.session.internal.dto.response.AudienceMemberResponse;
import com.pte.session.internal.dto.response.AudiencePreviewResponse;
import com.pte.session.internal.dto.response.AudienceSourceResponse;
import com.pte.session.internal.dto.response.ExamPreflightResponse;
import com.pte.session.internal.dto.response.GenerationJobResponse;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.exception.AudienceSourceNotFoundException;
import com.pte.session.internal.exception.ExamDraftConfigurationException;
import com.pte.session.internal.exception.ExamDraftNotEditableException;
import com.pte.session.internal.exception.ExamDraftVersionConflictException;
import com.pte.session.internal.exception.ExamPublishBlockedException;
import com.pte.session.internal.exception.GenerationNotReadyException;
import com.pte.session.internal.exception.HostContextRequiredException;
import com.pte.session.internal.exception.InvalidSessionWindowException;
import com.pte.session.internal.exception.SessionCapacityInvalidException;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.exception.SessionNotCancellableException;
import com.pte.session.internal.exception.SessionSubscriptionCapacityException;
import com.pte.session.internal.exception.SessionSubscriptionNotFoundException;
import com.pte.session.internal.exception.SessionTimeConflictException;
import com.pte.session.internal.exception.SessionWindowOutsideSubscriptionException;
import com.pte.session.internal.mapper.SessionMapper;
import com.pte.session.internal.repository.ExamAudienceMemberRepository;
import com.pte.session.internal.repository.ExamAudienceSourceRepository;
import com.pte.session.internal.repository.ExamFormRepository;
import com.pte.session.internal.repository.ExamGenerationJobRepository;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.session.internal.repository.EnrollmentRepository;
import com.pte.session.internal.repository.FormAssignmentRepository;
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateFeasibilityResponse;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.shared.StartedAttemptLookup;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.audit.AuditLogService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Canonical draft, audience, publish-gate and form orchestration service. */
@Service
public class ExamOrchestrationService {

    private static final String ALGORITHM_VERSION = "PTE_SEEDED_V1";

    private final ExamSessionRepository sessionRepository;
    private final ExamAudienceSourceRepository sourceRepository;
    private final ExamAudienceMemberRepository memberRepository;
    private final ExamGenerationJobRepository jobRepository;
    private final ExamFormRepository formRepository;
    private final FormAssignmentRepository formAssignmentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SessionLifecycleService sessionLifecycleService;
    private final EnrollmentService enrollmentService;
    private final AssessmentService assessmentService;
    private final ScoreTemplateService scoreTemplateService;
    private final BillingService billingService;
    private final EnrollmentModuleService enrollmentModuleService;
    private final IdentityService identityService;
    private final StartedAttemptLookup startedAttemptLookup;
    private final AuditLogService auditLogService;
    private final SecureRandom secureRandom = new SecureRandom();

    public ExamOrchestrationService(ExamSessionRepository sessionRepository,
            ExamAudienceSourceRepository sourceRepository,
            ExamAudienceMemberRepository memberRepository,
            ExamGenerationJobRepository jobRepository,
            ExamFormRepository formRepository,
            FormAssignmentRepository formAssignmentRepository,
            EnrollmentRepository enrollmentRepository,
            SessionLifecycleService sessionLifecycleService,
            EnrollmentService enrollmentService,
            AssessmentService assessmentService,
            ScoreTemplateService scoreTemplateService,
            BillingService billingService,
            EnrollmentModuleService enrollmentModuleService,
            IdentityService identityService,
            StartedAttemptLookup startedAttemptLookup,
            AuditLogService auditLogService) {
        this.sessionRepository = sessionRepository;
        this.sourceRepository = sourceRepository;
        this.memberRepository = memberRepository;
        this.jobRepository = jobRepository;
        this.formRepository = formRepository;
        this.formAssignmentRepository = formAssignmentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.sessionLifecycleService = sessionLifecycleService;
        this.enrollmentService = enrollmentService;
        this.assessmentService = assessmentService;
        this.scoreTemplateService = scoreTemplateService;
        this.billingService = billingService;
        this.enrollmentModuleService = enrollmentModuleService;
        this.identityService = identityService;
        this.startedAttemptLookup = startedAttemptLookup;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public SessionResponse createDraft(CreateExamDraftRequest request, CurrentUser caller) {
        UUID tenantId = requireTenant(caller);
        validateWindow(request.opensAt(), request.closesAt());
        SubscriptionView subscription = activeSubscription(request.subscriptionPublicId(), tenantId);
        validateSubscriptionWindowAndCapacity(subscription, request.opensAt(), request.closesAt(), request.capacity());
        var template = scoreTemplateService.findActiveByPublicId(request.templatePublicId())
                .orElseThrow(() -> new ExamDraftConfigurationException(SessionConstants.EXAM_TEMPLATE_ACTIVE_REQUIRED));

        ExamMode mode = request.examMode() == null ? ExamMode.MOCK_TEST : request.examMode();
        FormMode formMode = request.formMode() == null ? defaultFormMode(mode) : request.formMode();
        ReusePolicy reusePolicy = request.reusePolicy() == null ? defaultReusePolicy(mode) : request.reusePolicy();
        validateConfiguration(mode, formMode, reusePolicy, request.seriesKey());

        ExamSession session = new ExamSession();
        session.setName(request.name().trim());
        session.setTenantId(tenantId);
        session.setSubscriptionId(subscription.publicId());
        session.setLicenseKey(subscription.licenseKey());
        session.setTemplatePublicId(template.publicId());
        session.setTemplateVersion(template.version());
        session.setExamMode(mode);
        session.setFormMode(formMode);
        session.setReusePolicy(reusePolicy);
        session.setSeriesKey(normalizeSeriesKey(request.seriesKey()));
        Set<String> templateSkills = resolveTemplateSkills(template);
        session.setSelectedSkills(resolveSelectedSkills(request.selectedSkills(), templateSkills, mode));
        session.setMaxRetriesPerStudent(resolveRetryCount(request.maxRetriesPerStudent()));
        session.setOpensAt(request.opensAt());
        session.setClosesAt(request.closesAt());
        session.setCapacity(request.capacity());
        session.setPolicy(ExamPolicy.forMode(mode));
        session.setStatus(SessionStatus.DRAFT);
        ExamSession saved = sessionRepository.saveAndFlush(session);
        auditLogService.record(caller, SessionConstants.AGGREGATE_EXAM_SESSION, saved.getPublicId().toString(),
                SessionConstants.EVENT_EXAM_DRAFT_CREATED, saved.getName());
        return SessionMapper.toResponse(saved);
    }

    @Transactional
    public SessionResponse updateDraft(UUID publicId, PatchExamDraftRequest request, CurrentUser caller) {
        ExamSession session = ownedForEdit(publicId, caller);
        if (request.expectedVersion() != null && !request.expectedVersion().equals(session.getDraftVersion())) {
            throw new ExamDraftVersionConflictException();
        }
        ExamMode previousMode = session.getExamMode();
        UUID previousTemplatePublicId = session.getTemplatePublicId();
        Integer previousTemplateVersion = session.getTemplateVersion();
        ScoreTemplateResponse selectedTemplate = null;
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new ExamDraftConfigurationException(SessionConstants.SESSION_NAME_REQUIRED);
            }
            session.setName(request.name().trim());
        }
        if (request.templatePublicId() != null) {
            selectedTemplate = scoreTemplateService.findActiveByPublicId(request.templatePublicId())
                    .orElseThrow(() -> new ExamDraftConfigurationException(SessionConstants.EXAM_TEMPLATE_ACTIVE_REQUIRED));
            session.setTemplatePublicId(selectedTemplate.publicId());
            session.setTemplateVersion(selectedTemplate.version());
        }
        if (request.subscriptionPublicId() != null) {
            SubscriptionView subscription = activeSubscription(request.subscriptionPublicId(), caller.tenantId());
            session.setSubscriptionId(subscription.publicId());
            session.setLicenseKey(subscription.licenseKey());
        }
        if (request.opensAt() != null) session.setOpensAt(request.opensAt());
        if (request.closesAt() != null) session.setClosesAt(request.closesAt());
        if (request.capacity() != null) {
            if (request.capacity() <= 0) throw new SessionCapacityInvalidException();
            session.setCapacity(request.capacity());
        }
        if (request.examMode() != null) {
            session.setExamMode(request.examMode());
            session.setPolicy(ExamPolicy.forMode(request.examMode()));
        }
        if (request.formMode() != null) session.setFormMode(request.formMode());
        if (request.reusePolicy() != null) session.setReusePolicy(request.reusePolicy());
        if (request.seriesKey() != null) session.setSeriesKey(normalizeSeriesKey(request.seriesKey()));
        if (selectedTemplate == null) {
            if (session.getTemplatePublicId() == null) {
                throw new ExamDraftConfigurationException(SessionConstants.EXAM_TEMPLATE_ACTIVE_REQUIRED);
            }
            selectedTemplate = scoreTemplateService.getByPublicId(session.getTemplatePublicId());
        }
        Set<String> templateSkills = resolveTemplateSkills(selectedTemplate);
        boolean templateChanged = !java.util.Objects.equals(previousTemplatePublicId, session.getTemplatePublicId())
                || !java.util.Objects.equals(previousTemplateVersion, session.getTemplateVersion());
        boolean modeChanged = previousMode != session.getExamMode();
        Set<String> currentSkills = session.getSelectedSkills() == null
                ? Set.of() : new LinkedHashSet<>(session.getSelectedSkills());
        boolean resetSkillsToFull = templateChanged || currentSkills.isEmpty()
                || (modeChanged && session.getExamMode() != ExamMode.PRACTICE
                        && !currentSkills.equals(templateSkills));
        List<String> requestedSkills = request.selectedSkills();
        if (requestedSkills == null && !resetSkillsToFull) {
            requestedSkills = new ArrayList<>(currentSkills);
        }
        session.setSelectedSkills(resolveSelectedSkills(requestedSkills, templateSkills, session.getExamMode()));
        if (request.maxRetriesPerStudent() != null) {
            session.setMaxRetriesPerStudent(resolveRetryCount(request.maxRetriesPerStudent()));
        }
        validateWindow(session.getOpensAt(), session.getClosesAt());
        validateConfiguration(session.getExamMode(), session.getFormMode(), session.getReusePolicy(), session.getSeriesKey());
        SubscriptionView subscription = activeSubscription(session.getSubscriptionId(), caller.tenantId());
        validateSubscriptionWindowAndCapacity(subscription, session.getOpensAt(), session.getClosesAt(), session.getCapacity());
        ExamSession saved = sessionRepository.saveAndFlush(session);
        auditLogService.record(caller, SessionConstants.AGGREGATE_EXAM_SESSION, saved.getPublicId().toString(),
                SessionConstants.EVENT_EXAM_DRAFT_UPDATED, saved.getName());
        return SessionMapper.toResponse(saved);
    }

    @Transactional
    public AudienceSourceResponse addSource(UUID sessionPublicId, AudienceSourceRequest request, CurrentUser caller) {
        ExamSession session = ownedForEdit(sessionPublicId, caller);
        validateSourceOwnership(request, caller.tenantId());
        if (sourceRepository.existsBySessionIdAndSourceTypeAndSourcePublicId(
                session.getId(), request.sourceType(), request.sourcePublicId())) {
            return sourceRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                    .filter(source -> source.getSourceType() == request.sourceType()
                            && source.getSourcePublicId().equals(request.sourcePublicId()))
                    .findFirst()
                    .map(this::toSourceResponse)
                    .orElseThrow(AudienceSourceNotFoundException::new);
        }
        ExamAudienceSource source = new ExamAudienceSource();
        source.setSession(session);
        source.setTenantId(caller.tenantId());
        source.setSourceType(request.sourceType());
        source.setSourcePublicId(request.sourcePublicId());
        source.setCreatedBy(caller.userId());
        ExamAudienceSource saved = sourceRepository.save(source);
        auditLogService.record(caller, SessionConstants.AGGREGATE_EXAM_SESSION, session.getPublicId().toString(),
                SessionConstants.EVENT_EXAM_AUDIENCE_SOURCE_ADDED, request.sourceType().name());
        return toSourceResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AudienceSourceResponse> listSources(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = owned(sessionPublicId, caller);
        return sourceRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(this::toSourceResponse).toList();
    }

    @Transactional
    public void removeSource(UUID sessionPublicId, UUID sourcePublicId, CurrentUser caller) {
        ExamSession session = ownedForEdit(sessionPublicId, caller);
        ExamAudienceSource source = sourceRepository.findByPublicIdAndSessionId(sourcePublicId, session.getId())
                .orElseThrow(AudienceSourceNotFoundException::new);
        sourceRepository.delete(source);
        memberRepository.deleteBySessionId(session.getId());
        auditLogService.record(caller, SessionConstants.AGGREGATE_EXAM_SESSION, session.getPublicId().toString(),
                SessionConstants.EVENT_EXAM_AUDIENCE_SOURCE_REMOVED, source.getSourceType().name());
    }

    @Transactional
    public AudiencePreviewResponse previewAudience(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = owned(sessionPublicId, caller);
        if (session.getStatus() != SessionStatus.DRAFT) {
            throw new ExamDraftNotEditableException();
        }
        return resolveAudience(session, true);
    }

    @Transactional
    public ExamPreflightResponse preflight(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = owned(sessionPublicId, caller);
        if (session.getStatus() != SessionStatus.DRAFT && session.getStatus() != SessionStatus.READY) {
            throw new ExamDraftNotEditableException();
        }
        return preflightInternal(session, session.getStatus() == SessionStatus.DRAFT);
    }

    @Transactional
    public GenerationJobResponse generate(UUID sessionPublicId, String idempotencyKey, CurrentUser caller) {
        String normalizedIdempotencyKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (normalizedIdempotencyKey.isBlank()) {
            throw new ExamDraftConfigurationException(SessionConstants.EXAM_GENERATION_KEY_REQUIRED);
        }
        ExamSession session = sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller);
        if (session.getStatus() == SessionStatus.READY) {
            return latestJob(session);
        }
        if (session.getStatus() != SessionStatus.DRAFT) {
            throw new GenerationNotReadyException();
        }
        ExamGenerationJob existing = jobRepository.findBySessionIdAndIdempotencyKey(session.getId(), normalizedIdempotencyKey)
                .orElse(null);
        if (existing != null) {
            return toJobResponse(existing);
        }
        ExamPreflightResponse preflight = preflightInternal(session, true);
        if (!preflight.ready()) {
            throw new ExamPublishBlockedException(preflight);
        }

        List<ExamAudienceMember> eligible = memberRepository.findBySessionIdAndStatus(
                session.getId(), AudienceMemberStatus.ELIGIBLE);
        session.setStatus(SessionStatus.PREPARING);
        long baseSeed = secureRandom.nextLong();
        int formsTotal = session.getFormMode() == FormMode.UNIQUE_FORM_PER_STUDENT ? eligible.size() : 1;
        ExamGenerationJob job = new ExamGenerationJob();
        job.setSession(session);
        job.setTenantId(session.getTenantId());
        job.setStatus(GenerationJobStatus.RUNNING);
        job.setIdempotencyKey(normalizedIdempotencyKey);
        job.setAlgorithmVersion(ALGORITHM_VERSION);
        job.setBaseSeed(baseSeed);
        job.setFormsTotal(formsTotal);
        job.setFormsCompleted(0);
        job = jobRepository.saveAndFlush(job);
        session.setGenerationJobPublicId(job.getPublicId());

        formRepository.deleteBySessionId(session.getId());
        formAssignmentRepository.deleteBySessionId(session.getId());
        List<ExamForm> forms = new ArrayList<>();
        for (int index = 0; index < formsTotal; index++) {
            long formSeed = baseSeed ^ (0x9E3779B97F4A7C15L * (index + 1L));
            SnapshotResponse snapshot = assessmentService.generateDeterministic(
                    session.getName(), session.getTemplatePublicId(), formSeed, caller, session.getSelectedSkills());
            ExamForm form = new ExamForm();
            form.setSession(session);
            form.setSnapshotPublicId(snapshot.publicId());
            form.setFormIndex(index);
            form.setFormSeed(formSeed);
            form.setAlgorithmVersion(ALGORITHM_VERSION);
            form.setPoolPolicyFingerprint(session.getTemplatePublicId() + ":" + session.getTemplateVersion());
            forms.add(formRepository.saveAndFlush(form));
            job.setFormsCompleted(index + 1);
            jobRepository.save(job);
            if (index == 0) session.setSnapshotPublicId(snapshot.publicId());
        }
        if (session.getFormMode() == FormMode.SHARED_FORM) {
            for (ExamAudienceMember member : eligible) {
                formAssignmentRepository.save(newAssignment(session, forms.get(0), member.getStudentPublicId()));
            }
        } else {
            for (int index = 0; index < eligible.size(); index++) {
                ExamAudienceMember member = eligible.get(index);
                formAssignmentRepository.save(newAssignment(session, forms.get(index), member.getStudentPublicId()));
            }
        }
        job.setStatus(GenerationJobStatus.SUCCEEDED);
        jobRepository.save(job);
        session.setStatus(SessionStatus.READY);
        sessionRepository.save(session);
        auditLogService.record(caller, SessionConstants.AGGREGATE_EXAM_SESSION, session.getPublicId().toString(),
                SessionConstants.EVENT_EXAM_GENERATED, job.getPublicId().toString());
        return toJobResponse(job);
    }

    @Transactional(readOnly = true)
    public GenerationJobResponse generation(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = owned(sessionPublicId, caller);
        return latestJob(session);
    }

    @Transactional
    public SessionResponse publish(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller);
        if (session.getStatus() != SessionStatus.READY || session.getSnapshotPublicId() == null) {
            throw new GenerationNotReadyException();
        }
        ExamPreflightResponse preflight = preflightInternal(session, false);
        if (!preflight.ready()) {
            throw new ExamPublishBlockedException(preflight);
        }
        List<ExamAudienceMember> audienceMembers = memberRepository.findBySessionIdOrderByStudentPublicIdAsc(session.getId())
                .stream()
                .filter(member -> member.getStatus() != AudienceMemberStatus.EXCLUDED)
                .toList();
        enrollmentService.bulkEnrollPublishedExam(session,
                new BulkEnrollRequest(audienceMembers.stream().map(ExamAudienceMember::getStudentPublicId).toList()), caller);
        Instant publishedAt = Instant.now();
        audienceMembers.forEach(member -> {
            member.setStatus(AudienceMemberStatus.ENROLLED);
            member.setPublishedAt(publishedAt);
        });
        session.setStatus(SessionStatus.SCHEDULED);
        try {
            sessionRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new SessionTimeConflictException();
        }
        auditLogService.record(caller, SessionConstants.AGGREGATE_EXAM_SESSION, session.getPublicId().toString(),
                SessionConstants.EVENT_EXAM_PUBLISHED, session.getName());
        return SessionMapper.toResponse(session);
    }

    @Transactional
    public SessionResponse cancel(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller);
        if (session.getStatus() != SessionStatus.DRAFT && session.getStatus() != SessionStatus.PREPARING
                && session.getStatus() != SessionStatus.READY && session.getStatus() != SessionStatus.SCHEDULED) {
            throw new SessionNotCancellableException();
        }
        session.cancel();
        jobRepository.findFirstBySessionIdOrderByCreatedAtDesc(session.getId()).ifPresent(job -> {
            if (job.getStatus() == GenerationJobStatus.QUEUED || job.getStatus() == GenerationJobStatus.RUNNING) {
                job.setStatus(GenerationJobStatus.CANCELLED);
                jobRepository.save(job);
            }
        });
        sessionRepository.save(session);
        auditLogService.record(caller, SessionConstants.AGGREGATE_EXAM_SESSION, session.getPublicId().toString(),
                SessionConstants.EVENT_EXAM_CANCELLED, session.getName());
        return SessionMapper.toResponse(session);
    }

    private ExamPreflightResponse preflightInternal(ExamSession session, boolean persistAudience) {
        ScoreTemplateFeasibilityResponse template = assessmentService
                .getTemplateFeasibility(session.getTemplatePublicId(), session.getSelectedSkills());
        SubscriptionView subscription = billingService.getActiveSubscription(session.getSubscriptionId(), session.getTenantId())
                .orElse(null);
        boolean subscriptionReady = subscription != null;
        boolean windowReady = subscriptionReady && !session.getOpensAt().isBefore(subscription.startsAt())
                && !session.getClosesAt().isAfter(subscription.expiresAt());
        boolean overlapReady = sessionRepository.findFirstOverlapping(session.getSubscriptionId(),
                session.getOpensAt(), session.getClosesAt(), session.getPublicId()).isEmpty();
        AudiencePreviewResponse audience = resolveAudience(session, persistAudience);
        List<String> issues = new ArrayList<>();
        // Once generation succeeds, the snapshot/form set is immutable. A later
        // question-bank archive must not make an already generated READY exam
        // impossible to publish; the draft/generation preflight remains the
        // point where live pool feasibility is authoritative.
        if (session.getStatus() != SessionStatus.READY && !template.ready()) {
            issues.add(SessionConstants.PREFLIGHT_TEMPLATE_POOL_INSUFFICIENT);
        }
        if (!subscriptionReady) issues.add(SessionConstants.SESSION_SUBSCRIPTION_NOT_FOUND);
        if (!windowReady) issues.add(SessionConstants.SESSION_WINDOW_OUTSIDE_SUBSCRIPTION);
        if (!overlapReady) issues.add(SessionConstants.SESSION_TIME_CONFLICT);
        if (audience.candidateCount() == 0) issues.add(SessionConstants.PREFLIGHT_AUDIENCE_EMPTY);
        if (audience.candidateCount() > 0 && audience.eligibleCount() == 0) {
            issues.add(SessionConstants.PREFLIGHT_AUDIENCE_NO_ELIGIBLE);
        }
        if (audience.blockedCount() > 0) issues.add(SessionConstants.PREFLIGHT_AUDIENCE_CONFLICT_BLOCKED);
        if (!audience.capacityReady()) issues.add(SessionConstants.SESSION_CAPACITY_EXCEEDED);
        if (!persistAudience && session.getStatus() == SessionStatus.READY
                && audienceChangedAfterGeneration(session, audience)) {
            issues.add(SessionConstants.PREFLIGHT_AUDIENCE_CHANGED);
        }
        return new ExamPreflightResponse(issues.isEmpty(), template, audience, subscriptionReady,
                windowReady, overlapReady, issues);
    }

    private boolean audienceChangedAfterGeneration(ExamSession session, AudiencePreviewResponse current) {
        Set<UUID> currentEligible = current.members().stream()
                .filter(member -> member.status() == AudienceMemberStatus.ELIGIBLE)
                .map(AudienceMemberResponse::studentPublicId)
                .collect(Collectors.toSet());
        Set<UUID> generatedAudience = memberRepository.findBySessionIdOrderByStudentPublicIdAsc(session.getId()).stream()
                .filter(member -> member.getStatus() != AudienceMemberStatus.EXCLUDED)
                .map(ExamAudienceMember::getStudentPublicId)
                .collect(Collectors.toSet());
        return !currentEligible.equals(generatedAudience);
    }

    private AudiencePreviewResponse resolveAudience(ExamSession session, boolean persist) {
        List<ExamAudienceSource> sources = sourceRepository.findBySessionIdOrderByCreatedAtAsc(session.getId());
        Map<UUID, LinkedHashSet<String>> provenance = new LinkedHashMap<>();
        int totalResolved = 0;
        for (ExamAudienceSource source : sources) {
            List<UUID> students = resolveSource(source, session.getTenantId());
            totalResolved += students.size();
            for (UUID student : students) {
                provenance.computeIfAbsent(student, ignored -> new LinkedHashSet<>())
                        .add(source.getSourceType().name() + ":" + source.getSourcePublicId());
            }
        }

        List<UUID> studentIds = new ArrayList<>(provenance.keySet());
        List<Enrollment> priorEnrollments = studentIds.isEmpty() ? List.of()
                : enrollmentRepository.findByTenantIdAndStudentPublicIdIn(session.getTenantId(), studentIds);
        Map<UUID, List<Enrollment>> priorByStudent = priorEnrollments.stream()
                .filter(enrollment -> !enrollment.getSession().getPublicId().equals(session.getPublicId()))
                .collect(Collectors.groupingBy(Enrollment::getStudentPublicId));
        List<UUID> sameSeriesSessionIds = priorEnrollments.stream()
                .map(Enrollment::getSession)
                .filter(prior -> !prior.getPublicId().equals(session.getPublicId()))
                .filter(prior -> session.getSeriesKey() != null && session.getSeriesKey().equals(prior.getSeriesKey()))
                .map(ExamSession::getPublicId).distinct().toList();
        Set<UUID> startedStudents = session.getReusePolicy() == ReusePolicy.EXCLUDE_STARTED_IN_SERIES
                ? startedAttemptLookup.findStartedStudentPublicIds(session.getTenantId(), sameSeriesSessionIds, studentIds)
                : Set.of();

        List<ExamAudienceMember> members = new ArrayList<>();
        for (UUID student : studentIds) {
            ExamAudienceMember member = new ExamAudienceMember();
            member.setSession(session);
            member.setTenantId(session.getTenantId());
            member.setStudentPublicId(student);
            member.setSourceSummary(String.join(", ", provenance.get(student)));
            member.setStatus(AudienceMemberStatus.ELIGIBLE);
            List<Enrollment> prior = priorByStudent.getOrDefault(student, List.of());
            Enrollment conflict = prior.stream().filter(enrollment -> !enrollment.getSession().getStatus().equals(SessionStatus.CANCELLED))
                    .filter(enrollment -> session.getSeriesKey() != null
                            && session.getSeriesKey().equals(enrollment.getSession().getSeriesKey()))
                    .findFirst().orElse(null);
            if (session.getReusePolicy() == ReusePolicy.EXCLUDE_ASSIGNED_IN_SERIES && conflict != null) {
                exclude(member, AudienceDecisionReason.ALREADY_ASSIGNED, conflict);
            } else if (session.getReusePolicy() == ReusePolicy.EXCLUDE_STARTED_IN_SERIES && startedStudents.contains(student)) {
                exclude(member, AudienceDecisionReason.ALREADY_STARTED, conflict);
            } else if (session.getReusePolicy() == ReusePolicy.BLOCK_ON_SCHEDULE_OVERLAP) {
                Enrollment overlap = prior.stream().filter(enrollment -> overlaps(session, enrollment.getSession()))
                        .findFirst().orElse(null);
                if (overlap != null) exclude(member, AudienceDecisionReason.SCHEDULE_OVERLAP, overlap);
            }
            members.add(member);
        }

        List<ExamAudienceMember> eligible = members.stream()
                .filter(member -> member.getStatus() == AudienceMemberStatus.ELIGIBLE).toList();
        boolean capacityReady = eligible.size() <= session.getCapacity();
        if (!capacityReady) {
            for (int index = session.getCapacity(); index < eligible.size(); index++) {
                ExamAudienceMember member = eligible.get(index);
                member.setStatus(AudienceMemberStatus.EXCLUDED);
                member.setReason(AudienceDecisionReason.CAPACITY_EXCEEDED);
            }
        }
        if (persist) {
            // Preflight is intentionally repeatable: the UI calls it before generation and
            // generation calls it again. Replacing every row with a new entity instance can
            // make Hibernate execute the INSERT before the pending DELETE and violate the
            // session/student unique constraint. Reuse the existing rows and only add/remove
            // the delta so repeated preflight/generate requests are idempotent.
            List<ExamAudienceMember> existingMembers = memberRepository
                    .findBySessionIdOrderByStudentPublicIdAsc(session.getId());
            Map<UUID, ExamAudienceMember> existingByStudent = existingMembers.stream()
                    .collect(Collectors.toMap(ExamAudienceMember::getStudentPublicId, member -> member,
                            (first, ignored) -> first, LinkedHashMap::new));
            List<ExamAudienceMember> persistedMembers = members.stream()
                    .map(computed -> mergeAudienceMember(existingByStudent.get(computed.getStudentPublicId()), computed))
                    .toList();
            existingMembers.stream()
                    .filter(existing -> !provenance.containsKey(existing.getStudentPublicId()))
                    .forEach(memberRepository::delete);
            memberRepository.saveAll(persistedMembers);
            members = persistedMembers;
        }
        int eligibleCount = (int) members.stream().filter(member -> member.getStatus() == AudienceMemberStatus.ELIGIBLE).count();
        int excludedCount = (int) members.stream().filter(member -> member.getStatus() == AudienceMemberStatus.EXCLUDED).count();
        int blockedCount = (int) members.stream().filter(member -> member.getReason() == AudienceDecisionReason.SCHEDULE_OVERLAP).count();
        List<AudienceMemberResponse> responses = members.stream().map(this::toMemberResponse).toList();
        return new AudiencePreviewResponse(studentIds.size(), Math.max(0, totalResolved - studentIds.size()),
                eligibleCount, excludedCount, blockedCount, session.getCapacity(),
                blockedCount == 0 && capacityReady, responses);
    }

    private ExamAudienceMember mergeAudienceMember(ExamAudienceMember existing, ExamAudienceMember computed) {
        if (existing == null) return computed;
        existing.setTenantId(computed.getTenantId());
        existing.setStudentPublicId(computed.getStudentPublicId());
        existing.setStatus(computed.getStatus());
        existing.setReason(computed.getReason());
        existing.setSourceSummary(computed.getSourceSummary());
        existing.setPriorSessionPublicId(computed.getPriorSessionPublicId());
        existing.setPriorSessionName(computed.getPriorSessionName());
        existing.setPriorStatus(computed.getPriorStatus());
        existing.setPublishedAt(computed.getPublishedAt());
        return existing;
    }

    private List<UUID> resolveSource(ExamAudienceSource source, UUID tenantId) {
        return switch (source.getSourceType()) {
            case CLASS -> enrollmentModuleService.findActiveStudentPublicIds(tenantId, source.getSourcePublicId());
            case PROGRAM -> enrollmentModuleService.findActiveStudentPublicIdsByProgram(tenantId, source.getSourcePublicId());
            case STUDENT -> identityService.findById(source.getSourcePublicId())
                    .filter(user -> tenantId.equals(user.getTenantId()) && user.getRoles().contains(Role.STUDENT))
                    .map(User::getPublicId).stream().toList();
        };
    }

    private void validateSourceOwnership(AudienceSourceRequest request, UUID tenantId) {
        List<UUID> resolved = switch (request.sourceType()) {
            case CLASS -> enrollmentModuleService.findActiveStudentPublicIds(tenantId, request.sourcePublicId());
            case PROGRAM -> enrollmentModuleService.findActiveStudentPublicIdsByProgram(tenantId, request.sourcePublicId());
            case STUDENT -> identityService.findById(request.sourcePublicId())
                    .filter(user -> tenantId.equals(user.getTenantId()) && user.getRoles().contains(Role.STUDENT))
                    .map(User::getPublicId).stream().toList();
        };
        if (request.sourceType() == AudienceSourceType.STUDENT && resolved.isEmpty()) {
            throw new AudienceSourceNotFoundException();
        }
    }

    private void exclude(ExamAudienceMember member, AudienceDecisionReason reason, Enrollment conflict) {
        member.setStatus(AudienceMemberStatus.EXCLUDED);
        member.setReason(reason);
        if (conflict != null) {
            member.setPriorSessionPublicId(conflict.getSession().getPublicId());
            member.setPriorSessionName(conflict.getSession().getName());
            member.setPriorStatus(conflict.getSession().getStatus().name());
        }
    }

    private boolean overlaps(ExamSession left, ExamSession right) {
        return left.getOpensAt().isBefore(right.getClosesAt()) && right.getOpensAt().isBefore(left.getClosesAt())
                && right.getStatus() != SessionStatus.CANCELLED;
    }

    private FormAssignment newAssignment(ExamSession session, ExamForm form, UUID studentPublicId) {
        FormAssignment assignment = new FormAssignment();
        assignment.setSession(session);
        assignment.setForm(form);
        assignment.setStudentPublicId(studentPublicId);
        return assignment;
    }

    private ExamSession ownedForEdit(UUID publicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwnedWithLock(publicId, caller);
        if (session.getStatus() != SessionStatus.DRAFT) throw new ExamDraftNotEditableException();
        return session;
    }

    private ExamSession owned(UUID publicId, CurrentUser caller) {
        return sessionRepository.findByPublicIdAndTenantId(publicId, requireTenant(caller))
                .orElseThrow(SessionNotFoundException::new);
    }

    private GenerationJobResponse latestJob(ExamSession session) {
        return jobRepository.findFirstBySessionIdOrderByCreatedAtDesc(session.getId())
                .map(this::toJobResponse).orElseThrow(GenerationNotReadyException::new);
    }

    private GenerationJobResponse toJobResponse(ExamGenerationJob job) {
        return new GenerationJobResponse(job.getPublicId(), job.getSession().getPublicId(), job.getStatus(),
                job.getFormsTotal(), job.getFormsCompleted(), job.getAlgorithmVersion());
    }

    private AudienceSourceResponse toSourceResponse(ExamAudienceSource source) {
        return new AudienceSourceResponse(source.getPublicId(), source.getSourceType(), source.getSourcePublicId());
    }

    private AudienceMemberResponse toMemberResponse(ExamAudienceMember member) {
        return new AudienceMemberResponse(member.getStudentPublicId(), member.getStatus(), member.getReason(),
                member.getSourceSummary(), member.getPriorSessionPublicId(), member.getPriorSessionName(),
                member.getPriorStatus());
    }

    private SubscriptionView activeSubscription(UUID publicId, UUID tenantId) {
        return billingService.getActiveSubscription(publicId, tenantId)
                .orElseThrow(SessionSubscriptionNotFoundException::new);
    }

    private void validateSubscriptionWindowAndCapacity(SubscriptionView subscription, Instant opensAt,
            Instant closesAt, int capacity) {
        if (subscription == null || !subscription.isUsableAt(Instant.now()))
            throw new SessionSubscriptionNotFoundException();
        if (opensAt.isBefore(subscription.startsAt()) || closesAt.isAfter(subscription.expiresAt()))
            throw new SessionWindowOutsideSubscriptionException();
        if (capacity <= 0) throw new SessionCapacityInvalidException();
        if (capacity > subscription.maxStudentsPerSession())
            throw new SessionSubscriptionCapacityException(capacity, subscription.maxStudentsPerSession());
    }

    private void validateWindow(Instant opensAt, Instant closesAt) {
        if (opensAt == null || closesAt == null || !closesAt.isAfter(opensAt))
            throw new InvalidSessionWindowException();
    }

    private void validateConfiguration(ExamMode mode, FormMode formMode, ReusePolicy reusePolicy, String seriesKey) {
        if (mode != ExamMode.PRACTICE && formMode != FormMode.UNIQUE_FORM_PER_STUDENT) {
            throw new ExamDraftConfigurationException(SessionConstants.EXAM_OFFICIAL_UNIQUE_FORM_REQUIRED);
        }
        if (mode != ExamMode.PRACTICE && reusePolicy != ReusePolicy.ALLOW
                && (seriesKey == null || seriesKey.isBlank())) {
            throw new ExamDraftConfigurationException(SessionConstants.EXAM_SERIES_REQUIRED);
        }
    }

    private Set<String> resolveTemplateSkills(ScoreTemplateResponse template) {
        if (template == null || template.items() == null || template.items().isEmpty()) {
            throw new ExamDraftConfigurationException(SessionConstants.TEMPLATE_SKILLS_UNAVAILABLE);
        }
        LinkedHashSet<String> skills = new LinkedHashSet<>();
        for (var item : template.items()) {
            String section = normalizeSkill(item.section());
            if (section == null) {
                continue;
            }
            if (!SessionConstants.SUPPORTED_EXAM_SKILLS.contains(section)) {
                throw new ExamDraftConfigurationException(SessionConstants.TEMPLATE_SKILLS_UNAVAILABLE);
            }
            skills.add(section);
        }
        if (skills.isEmpty()) {
            throw new ExamDraftConfigurationException(SessionConstants.TEMPLATE_SKILLS_UNAVAILABLE);
        }
        return skills;
    }

    private Set<String> resolveSelectedSkills(List<String> requestedSkills, Set<String> templateSkills, ExamMode mode) {
        if (requestedSkills == null) {
            return new LinkedHashSet<>(templateSkills);
        }
        if (requestedSkills.isEmpty()) {
            throw new ExamDraftConfigurationException(SessionConstants.SKILLS_REQUIRED);
        }
        LinkedHashSet<String> selectedSkills = new LinkedHashSet<>();
        for (String value : requestedSkills) {
            String skill = normalizeSkill(value);
            if (skill == null || !templateSkills.contains(skill)) {
                throw new ExamDraftConfigurationException(SessionConstants.SKILLS_NOT_IN_TEMPLATE);
            }
            if (!selectedSkills.add(skill)) {
                throw new ExamDraftConfigurationException(SessionConstants.SKILLS_DUPLICATE);
            }
        }
        if (mode != ExamMode.PRACTICE && !selectedSkills.equals(templateSkills)) {
            throw new ExamDraftConfigurationException(SessionConstants.SKILLS_FULL_TEMPLATE_REQUIRED);
        }
        return selectedSkills;
    }

    private String normalizeSkill(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private int resolveRetryCount(Integer retries) {
        if (retries == null) {
            return 0;
        }
        if (retries < 0 || retries > 9) {
            throw new ExamDraftConfigurationException(SessionConstants.RETRY_COUNT_INVALID);
        }
        return retries;
    }

    private FormMode defaultFormMode(ExamMode mode) {
        return mode == ExamMode.PRACTICE ? FormMode.SHARED_FORM : FormMode.UNIQUE_FORM_PER_STUDENT;
    }

    private ReusePolicy defaultReusePolicy(ExamMode mode) {
        return mode == ExamMode.PRACTICE ? ReusePolicy.ALLOW : ReusePolicy.EXCLUDE_STARTED_IN_SERIES;
    }

    private String normalizeSeriesKey(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private UUID requireTenant(CurrentUser caller) {
        if (caller == null || caller.tenantId() == null) throw new HostContextRequiredException();
        return caller.tenantId();
    }
}
