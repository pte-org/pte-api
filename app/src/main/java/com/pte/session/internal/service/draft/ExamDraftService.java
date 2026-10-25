package com.pte.session.internal.service.draft;

import com.pte.session.internal.service.ExamSessionAccess;
import com.pte.session.internal.service.SessionLifecycleService;
import com.pte.billing.BillingService;
import com.pte.billing.SubscriptionView;
import com.pte.session.domain.ExamPolicy;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.ExamMode;
import com.pte.session.domain.enums.LockdownMode;
import com.pte.session.domain.enums.ReusePolicy;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.dto.request.CreateExamDraftRequest;
import com.pte.session.internal.dto.request.PatchExamDraftRequest;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.exception.draft.ExamDraftConfigurationException;
import com.pte.session.internal.exception.draft.ExamDraftNotDeletableException;
import com.pte.session.internal.exception.draft.ExamDraftNotEditableException;
import com.pte.session.internal.exception.draft.ExamDraftVersionConflictException;
import com.pte.session.internal.exception.capacity.InvalidSessionWindowException;
import com.pte.session.internal.exception.capacity.SessionCapacityInvalidException;
import com.pte.session.internal.exception.capacity.SessionSubscriptionCapacityException;
import com.pte.session.internal.exception.capacity.SessionSubscriptionNotFoundException;
import com.pte.session.internal.exception.capacity.SessionWindowOutsideSubscriptionException;
import com.pte.session.internal.mapper.SessionMapper;
import com.pte.session.internal.policy.SessionPolicyResolver;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Creates, edits and deletes an exam draft: window, subscription, definition, skills and policy. */
@Service
public class ExamDraftService {

    private final ExamSessionRepository sessionRepository;
    private final ExamSessionAccess access;
    private final ExamSkillSelection skills;
    private final SessionLifecycleService sessionLifecycleService;
    private final ExamDefinitionService examDefinitionService;
    private final BillingService billingService;
    private final SessionCodeGenerator sessionCodeGenerator;
    private final Clock clock;

    public ExamDraftService(ExamSessionRepository sessionRepository,
            ExamSessionAccess access,
            ExamSkillSelection skills,
            SessionLifecycleService sessionLifecycleService,
            ExamDefinitionService examDefinitionService,
            BillingService billingService,
            SessionCodeGenerator sessionCodeGenerator,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.access = access;
        this.skills = skills;
        this.sessionLifecycleService = sessionLifecycleService;
        this.examDefinitionService = examDefinitionService;
        this.billingService = billingService;
        this.sessionCodeGenerator = sessionCodeGenerator;
        this.clock = clock;
    }

    @Transactional
    public SessionResponse createDraft(CreateExamDraftRequest request, CurrentUser caller) {
        UUID tenantId = access.requireTenant(caller);
        access.assertTenantActive(tenantId);
        validateWindow(request.opensAt(), request.closesAt());
        SubscriptionView subscription = access.lockedActiveSubscription(request.subscriptionPublicId(), tenantId);
        validateSubscriptionWindowAndCapacity(subscription, request.opensAt(), request.closesAt(), request.capacity());
        var template = examDefinitionService.findActiveByPublicId(request.examDefinitionPublicId())
                .orElseThrow(() -> new ExamDraftConfigurationException(SessionConstants.EXAM_TEMPLATE_ACTIVE_REQUIRED));

        ExamMode mode = request.examMode() == null ? ExamMode.OFFICIAL_EXAM : request.examMode();
        ReusePolicy reusePolicy = request.reusePolicy() == null
                ? ReusePolicy.EXCLUDE_STARTED_IN_SERIES : request.reusePolicy();
        validateConfiguration(reusePolicy, request.seriesKey());

        ExamSession session = new ExamSession();
        session.setName(request.name().trim());
        session.setTenantId(tenantId);
        session.setSubscriptionId(subscription.publicId());
        session.setLicenseKey(subscription.licenseKey());
        session.setExamDefinitionPublicId(template.publicId());
        session.setExamDefinitionVersion(template.version());
        session.setExamMode(mode);
        session.setReusePolicy(reusePolicy);
        session.setSeriesKey(normalizeSeriesKey(request.seriesKey()));
        Set<String> templateSkills = skills.resolveTemplateSkills(template);
        session.setSelectedSkills(skills.resolveSelectedSkills(request.selectedSkills(), templateSkills));
        session.setMaxRetriesPerStudent(skills.resolveRetryCount(request.maxRetriesPerStudent()));
        session.setOpensAt(request.opensAt());
        session.setClosesAt(request.closesAt());
        session.setCapacity(request.capacity());
        session.setPolicy(ExamPolicy.realExamDefault());
        session.getPolicy().setLockdownMode(SessionPolicyResolver.resolveForCreate(request.lockdownMode()));
        session.setStatus(SessionStatus.DRAFT);
        session.setSessionCode(sessionCodeGenerator.generate(tenantId, request.opensAt()));
        ExamSession saved = sessionRepository.saveAndFlush(session);
        access.recordExamAudit(caller, saved, SessionConstants.EVENT_EXAM_DRAFT_CREATED, saved.getName());
        return SessionMapper.toResponse(saved);
    }

    @Transactional
    public SessionResponse updateDraft(UUID publicId, PatchExamDraftRequest request, CurrentUser caller) {
        UUID tenantId = access.requireTenant(caller);
        access.assertTenantActive(tenantId);
        ExamSession observed = access.observedSession(publicId, caller);
        if (request.expectedVersion() != null
                && !request.expectedVersion().equals(observed.getDraftVersion())) {
            throw new ExamDraftVersionConflictException();
        }
        UUID targetSubscriptionId = request.subscriptionPublicId() == null
                ? observed.getSubscriptionId() : request.subscriptionPublicId();
        if (observed.getSubscriptionId() == null || targetSubscriptionId == null) {
            throw new SessionSubscriptionNotFoundException();
        }
        List<UUID> subscriptionLockOrder = List.of(observed.getSubscriptionId(), targetSubscriptionId)
                .stream().sorted().toList();
        List<SubscriptionView> lockedSubscriptions = billingService.lockSubscriptions(
                subscriptionLockOrder, tenantId);
        SubscriptionView currentSubscription = lockedSubscriptions.stream()
                .filter(subscription -> subscription.publicId().equals(observed.getSubscriptionId()))
                .findFirst()
                .orElseGet(() -> access.exactOrActiveSubscription(observed.getSubscriptionId(), tenantId));
        SubscriptionView lockedSubscription = lockedSubscriptions.stream()
                .filter(subscription -> subscription.publicId().equals(targetSubscriptionId))
                .findFirst()
                .orElseGet(() -> access.exactOrActiveSubscription(targetSubscriptionId, tenantId));
        ExamSession session = access.lockedSession(publicId, caller, tenantId);
        if (session.getStatus() != SessionStatus.DRAFT) {
            throw new ExamDraftNotEditableException();
        }
        if (!java.util.Objects.equals(observed.getSubscriptionId(), session.getSubscriptionId())
                || !java.util.Objects.equals(currentSubscription.publicId(), session.getSubscriptionId())) {
            throw new com.pte.session.internal.exception.capacity.SessionSubscriptionConflictException();
        }
        if (request.expectedVersion() != null && !request.expectedVersion().equals(session.getDraftVersion())) {
            throw new ExamDraftVersionConflictException();
        }
        boolean legacyPolicyState = session.getExamMode() == null;
        LockdownMode previousLockdownMode = session.getPolicy() == null ? null : session.getPolicy().getLockdownMode();
        UUID previousTemplatePublicId = session.getExamDefinitionPublicId();
        Integer previousTemplateVersion = session.getExamDefinitionVersion();
        ExamDefinitionResponse selectedTemplate = null;
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new ExamDraftConfigurationException(SessionConstants.SESSION_NAME_REQUIRED);
            }
            session.setName(request.name().trim());
        }
        if (request.examDefinitionPublicId() != null) {
            selectedTemplate = examDefinitionService.findActiveByPublicId(request.examDefinitionPublicId())
                    .orElseThrow(() -> new ExamDraftConfigurationException(SessionConstants.EXAM_TEMPLATE_ACTIVE_REQUIRED));
            session.setExamDefinitionPublicId(selectedTemplate.publicId());
            session.setExamDefinitionVersion(selectedTemplate.version());
        }
        if (request.subscriptionPublicId() != null) {
            session.setSubscriptionId(lockedSubscription.publicId());
            session.setLicenseKey(lockedSubscription.licenseKey());
        }
        if (request.opensAt() != null) session.setOpensAt(request.opensAt());
        if (request.closesAt() != null) session.setClosesAt(request.closesAt());
        if (request.capacity() != null) {
            if (request.capacity() <= 0) throw new SessionCapacityInvalidException();
            session.setCapacity(request.capacity());
        }
        LockdownMode resolvedLockdownMode = SessionPolicyResolver.resolveForDraftPatch(
                legacyPolicyState, previousLockdownMode, request.lockdownMode());
        if (request.examMode() != null) {
            session.setExamMode(request.examMode());
        }
        if (session.getPolicy() == null) {
            throw new ExamDraftConfigurationException(SessionConstants.EXAM_POLICY_INCOMPLETE);
        }
        session.getPolicy().setLockdownMode(resolvedLockdownMode);
        if (request.reusePolicy() != null) session.setReusePolicy(request.reusePolicy());
        if (request.seriesKey() != null) session.setSeriesKey(normalizeSeriesKey(request.seriesKey()));
        if (selectedTemplate == null) {
            if (session.getExamDefinitionPublicId() == null) {
                throw new ExamDraftConfigurationException(SessionConstants.EXAM_TEMPLATE_ACTIVE_REQUIRED);
            }
            selectedTemplate = examDefinitionService.getByPublicId(session.getExamDefinitionPublicId());
        }
        Set<String> templateSkills = skills.resolveTemplateSkills(selectedTemplate);
        boolean templateChanged = !java.util.Objects.equals(previousTemplatePublicId, session.getExamDefinitionPublicId())
                || !java.util.Objects.equals(previousTemplateVersion, session.getExamDefinitionVersion());
        Set<String> currentSkills = session.getSelectedSkills() == null
                ? Set.of() : new LinkedHashSet<>(session.getSelectedSkills());
        boolean resetSkillsToFull = templateChanged || currentSkills.isEmpty();
        List<String> requestedSkills = request.selectedSkills();
        if (requestedSkills == null && !resetSkillsToFull) {
            requestedSkills = new ArrayList<>(currentSkills);
        }
        session.setSelectedSkills(skills.resolveSelectedSkills(requestedSkills, templateSkills));
        if (request.maxRetriesPerStudent() != null) {
            session.setMaxRetriesPerStudent(skills.resolveRetryCount(request.maxRetriesPerStudent()));
        }
        validateWindow(session.getOpensAt(), session.getClosesAt());
        validateConfiguration(session.getReusePolicy(), session.getSeriesKey());
        validateSubscriptionWindowAndCapacity(lockedSubscription, session.getOpensAt(),
                session.getClosesAt(), session.getCapacity());
        ExamSession saved = sessionRepository.saveAndFlush(session);
        access.recordExamAudit(caller, saved, SessionConstants.EVENT_EXAM_DRAFT_UPDATED, saved.getName());
        return SessionMapper.toResponse(saved);
    }

    @Transactional
    public void deleteDraft(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller);
        if (session.getStatus() != SessionStatus.DRAFT) {
            throw new ExamDraftNotDeletableException();
        }
        session.setDeleted(true);
        sessionRepository.save(session);
        access.recordExamAudit(caller, session, SessionConstants.EVENT_EXAM_DRAFT_DELETED, session.getName());
    }

    private void validateSubscriptionWindowAndCapacity(SubscriptionView subscription, Instant opensAt,
            Instant closesAt, int capacity) {
        if (subscription == null || !subscription.isUsableAt(clock.instant()))
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

    private void validateConfiguration(ReusePolicy reusePolicy, String seriesKey) {
        if (reusePolicy != ReusePolicy.ALLOW && (seriesKey == null || seriesKey.isBlank())) {
            throw new ExamDraftConfigurationException(SessionConstants.EXAM_SERIES_REQUIRED);
        }
    }

    private String normalizeSeriesKey(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
