package com.pte.session.internal.service.draft;

import com.pte.session.internal.service.ExamSessionAccess;
import com.pte.session.internal.service.SessionLifecycleService;
import com.pte.session.internal.service.audience.AudienceResolver;
import com.pte.session.internal.service.enrollment.EnrollmentService;
import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.billing.BillingService;
import com.pte.billing.SubscriptionView;
import com.pte.session.domain.ExamRegistration;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.AudienceDecisionReason;
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.session.domain.enums.GenerationJobStatus;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.response.SlotConflict;
import com.pte.session.dto.event.SessionCancelledEvent;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.dto.request.BulkEnrollRequest;
import com.pte.session.internal.dto.response.audience.AudiencePreviewResponse;
import com.pte.session.internal.dto.response.enrollment.BulkEnrollResponse;
import com.pte.session.internal.dto.response.ExamPreflightResponse;
import com.pte.session.internal.dto.response.GenerationJobResponse;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.exception.draft.ExamDraftConfigurationException;
import com.pte.session.internal.exception.draft.ExamDraftNotEditableException;
import com.pte.session.internal.exception.audience.ExamAudienceConflictException;
import com.pte.session.internal.exception.audience.ExamAudienceReconciliationException;
import com.pte.session.internal.exception.draft.ExamPublishBlockedException;
import com.pte.session.internal.exception.draft.GenerationNotReadyException;
import com.pte.session.internal.exception.lifecycle.SessionNotCancellableException;
import com.pte.session.internal.exception.capacity.SessionTimeConflictException;
import com.pte.session.internal.mapper.SessionMapper;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.session.internal.repository.ExamRegistrationRepository;
import com.pte.assessment.dto.response.ExamDefinitionFeasibilityResponse;
import com.pte.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.stream.Collectors;

/** Preflight, generation, publish and cancellation: moves an exam from draft to scheduled. */
@Service
public class ExamPublishService {

    private final ExamSessionRepository sessionRepository;
    private final ExamSessionAccess access;
    private final SessionLifecycleService sessionLifecycleService;
    private final EnrollmentService enrollmentService;
    private final AssessmentService assessmentService;
    private final BillingService billingService;
    private final AudienceResolver resolver;
    private final ExamRegistrationRepository registrationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public ExamPublishService(ExamSessionRepository sessionRepository,
            ExamSessionAccess access,
            SessionLifecycleService sessionLifecycleService,
            EnrollmentService enrollmentService,
            AssessmentService assessmentService,
            BillingService billingService,
            AudienceResolver resolver,
            ExamRegistrationRepository registrationRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.access = access;
        this.sessionLifecycleService = sessionLifecycleService;
        this.enrollmentService = enrollmentService;
        this.assessmentService = assessmentService;
        this.billingService = billingService;
        this.resolver = resolver;
        this.registrationRepository = registrationRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public ExamPreflightResponse preflight(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = access.owned(sessionPublicId, caller);
        if (session.getStatus() != SessionStatus.DRAFT && session.getStatus() != SessionStatus.READY) {
            throw new ExamDraftNotEditableException();
        }
        boolean persistAudience = session.getStatus() == SessionStatus.DRAFT;
        if (persistAudience) {
            access.assertTenantActive(session.getTenantId());
        }
        return preflightInternal(session, persistAudience);
    }

    /**
     * Builds the session's single frozen exam from its definition, once. Re-calling on a generated (READY)
     * session replays the existing result, so the idempotency key only has to be present, not stored.
     */
    @Transactional
    public GenerationJobResponse generate(UUID sessionPublicId, String idempotencyKey, CurrentUser caller) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ExamDraftConfigurationException(SessionConstants.EXAM_GENERATION_KEY_REQUIRED);
        }
        ExamSession session = sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller);
        access.assertTenantActive(session.getTenantId());
        if (session.getStatus() == SessionStatus.READY) {
            return generationOf(session);
        }
        if (session.getStatus() != SessionStatus.DRAFT) {
            throw new GenerationNotReadyException();
        }
        ExamPreflightResponse preflight = preflightInternal(session, true);
        if (!preflight.ready()) {
            throw new ExamPublishBlockedException(preflight);
        }
        SnapshotResponse exam = assessmentService.generateDeterministic(session.getName(),
                session.getExamDefinitionPublicId(), secureRandom.nextLong(), caller, session.getSelectedSkills());
        session.setExamVersionPublicId(exam.publicId());
        session.setStatus(SessionStatus.READY);
        sessionRepository.save(session);
        access.recordExamAudit(caller, session, SessionConstants.EVENT_EXAM_GENERATED, session.getName());
        return generationOf(session);
    }

    @Transactional(readOnly = true)
    public GenerationJobResponse generation(UUID sessionPublicId, CurrentUser caller) {
        return generationOf(access.owned(sessionPublicId, caller));
    }

    @Transactional(noRollbackFor = ExamAudienceConflictException.class)
    public SessionResponse publish(UUID sessionPublicId, CurrentUser caller) {
        UUID tenantId = access.requireTenant(caller);
        access.assertTenantActive(tenantId);
        ExamSession observed = access.observedSession(sessionPublicId, caller);
        SubscriptionView lockedSubscription = access.lockedActiveSubscription(
                observed.getSubscriptionId(), tenantId);
        ExamSession session = access.lockedSession(sessionPublicId, caller, tenantId);
        if (!java.util.Objects.equals(lockedSubscription.publicId(), session.getSubscriptionId())) {
            throw new com.pte.session.internal.exception.capacity.SessionSubscriptionConflictException();
        }
        if (session.getStatus() != SessionStatus.READY || session.getExamVersionPublicId() == null) {
            throw new GenerationNotReadyException();
        }
        ExamPreflightResponse preflight = preflightInternal(session, false);
        if (!preflight.ready()) {
            throw new ExamPublishBlockedException(preflight);
        }
        List<ExamRegistration> audienceMembers = registrationRepository.findBySessionIdOrderByStudentPublicIdAsc(session.getId())
                .stream()
                .filter(member -> member.getStatus() != AudienceMemberStatus.EXCLUDED)
                .toList();
        BulkEnrollResponse enrollmentResponse = enrollmentService.bulkEnrollPublishedExam(session,
                new BulkEnrollRequest(audienceMembers.stream().map(ExamRegistration::getStudentPublicId).toList()));
        Instant publishedAt = clock.instant();
        reconcilePublishedAudience(audienceMembers, enrollmentResponse, publishedAt);
        session.setStatus(SessionStatus.SCHEDULED);
        try {
            sessionRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new SessionTimeConflictException();
        }
        access.recordExamAudit(caller, session, SessionConstants.EVENT_EXAM_PUBLISHED, session.getName());
        return SessionMapper.toResponse(session);
    }

    private void reconcilePublishedAudience(List<ExamRegistration> audienceMembers,
            BulkEnrollResponse enrollmentResponse, Instant publishedAt) {
        Set<UUID> successfulStudentIds = new LinkedHashSet<>();
        successfulStudentIds.addAll(enrollmentResponse.enrolled());
        successfulStudentIds.addAll(enrollmentResponse.alreadyEnrolled());
        Map<UUID, SlotConflict> conflictsByStudent = enrollmentResponse.skippedByConflict().stream()
                .collect(Collectors.toMap(SlotConflict::studentPublicId, conflict -> conflict,
                        (first, ignored) -> first, LinkedHashMap::new));

        Set<UUID> audienceStudentIds = audienceMembers.stream()
                .map(ExamRegistration::getStudentPublicId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<UUID> unresolvedStudentIds = new ArrayList<>();
        for (UUID studentPublicId : audienceStudentIds) {
            if (successfulStudentIds.contains(studentPublicId) == conflictsByStudent.containsKey(studentPublicId)) {
                unresolvedStudentIds.add(studentPublicId);
            }
        }
        successfulStudentIds.stream()
                .filter(studentPublicId -> !audienceStudentIds.contains(studentPublicId))
                .forEach(unresolvedStudentIds::add);
        conflictsByStudent.keySet().stream()
                .filter(studentPublicId -> !audienceStudentIds.contains(studentPublicId))
                .forEach(unresolvedStudentIds::add);
        if (!unresolvedStudentIds.isEmpty()) {
            throw new ExamAudienceReconciliationException(unresolvedStudentIds.stream().distinct().toList());
        }

        audienceMembers.forEach(member -> {
            UUID studentPublicId = member.getStudentPublicId();
            SlotConflict conflict = conflictsByStudent.get(studentPublicId);
            if (successfulStudentIds.contains(studentPublicId)) {
                member.setStatus(AudienceMemberStatus.ENROLLED);
                member.setReason(null);
                member.setPriorSessionPublicId(null);
                member.setPriorSessionName(null);
                member.setPriorStatus(null);
                member.setPublishedAt(publishedAt);
            } else {
                member.setStatus(AudienceMemberStatus.EXCLUDED);
                member.setReason(com.pte.session.domain.enums.AudienceDecisionReason.SCHEDULE_OVERLAP);
                member.setPriorSessionPublicId(conflict.conflictingSessionPublicId());
                member.setPriorSessionName(conflict.conflictingSessionName());
                member.setPriorStatus(null);
                member.setPublishedAt(null);
            }
        });
        registrationRepository.saveAll(audienceMembers);
        if (successfulStudentIds.isEmpty() && !enrollmentResponse.skippedByConflict().isEmpty()) {
            registrationRepository.flush();
            throw new ExamAudienceConflictException(enrollmentResponse.skippedByConflict());
        }
    }

    @Transactional
    public SessionResponse cancel(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller);
        SessionStatus previousStatus = session.getStatus();
        if (previousStatus != SessionStatus.READY && previousStatus != SessionStatus.SCHEDULED) {
            throw new SessionNotCancellableException();
        }

        session.cancel();
        sessionRepository.save(session);
        access.recordExamAudit(caller, session, SessionConstants.EVENT_EXAM_CANCELLED,
                transitionSummary(session.getName(), previousStatus, session.getStatus()));
        eventPublisher.publishEvent(new SessionCancelledEvent(session.getPublicId(), session.getTenantId(),
                session.getName(), clock.instant()));
        return SessionMapper.toResponse(session);
    }

    private String transitionSummary(String name, SessionStatus from, SessionStatus to) {
        return "%s (%s -> %s)".formatted(name, from, to);
    }

    private ExamPreflightResponse preflightInternal(ExamSession session, boolean persistAudience) {
        ExamDefinitionFeasibilityResponse template = assessmentService
                .getTemplateFeasibility(session.getExamDefinitionPublicId(), session.getSelectedSkills());
        SubscriptionView subscription = billingService.getActiveSubscription(session.getSubscriptionId(), session.getTenantId())
                .orElse(null);
        boolean subscriptionReady = subscription != null;
        boolean windowReady = subscriptionReady && !session.getOpensAt().isBefore(subscription.startsAt())
                && !session.getClosesAt().isAfter(subscription.expiresAt());
        boolean overlapReady = sessionRepository.findFirstOverlapping(session.getSubscriptionId(),
                session.getOpensAt(), session.getClosesAt(), session.getPublicId()).isEmpty();
        AudiencePreviewResponse audience = resolver.resolve(session, persistAudience);
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
                && resolver.changedAfterGeneration(session, audience)) {
            issues.add(SessionConstants.PREFLIGHT_AUDIENCE_CHANGED);
        }
        return new ExamPreflightResponse(issues.isEmpty(), template, audience, subscriptionReady,
                windowReady, overlapReady, issues);
    }

    private GenerationJobResponse generationOf(ExamSession session) {
        if (session.getExamVersionPublicId() == null) {
            throw new GenerationNotReadyException();
        }
        return new GenerationJobResponse(session.getExamVersionPublicId(), session.getPublicId(),
                GenerationJobStatus.SUCCEEDED, 1, 1, SessionConstants.EXAM_GENERATION_ALGORITHM);
    }
}
