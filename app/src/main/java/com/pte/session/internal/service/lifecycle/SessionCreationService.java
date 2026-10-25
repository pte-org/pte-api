package com.pte.session.internal.service.lifecycle;

import com.pte.assessment.AssessmentService;
import com.pte.assessment.dto.response.SnapshotResponse;
import com.pte.billing.SubscriptionView;
import com.pte.session.domain.ExamPolicy;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.ExamMode;
import com.pte.session.internal.dto.request.CreateSessionRequest;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.exception.SessionCapacityInvalidException;
import com.pte.session.internal.exception.SessionCapacityRequiredException;
import com.pte.session.internal.mapper.SessionMapper;
import com.pte.session.internal.policy.SessionPolicyResolver;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import com.pte.session.internal.service.SessionCodeGenerator;

/** Creates the assessment snapshot and session atomically after subscription validation. */
@Service
public class SessionCreationService {

    private final ExamSessionRepository sessionRepository;
    private final AssessmentService assessmentService;
    private final SessionCodeGenerator sessionCodeGenerator;
    private final SessionAccess access;
    private final SessionSubscriptionRules subscriptions;
    private final TenancyService tenancyService;

    public SessionCreationService(ExamSessionRepository sessionRepository,
            AssessmentService assessmentService,
            SessionCodeGenerator sessionCodeGenerator,
            SessionAccess access,
            SessionSubscriptionRules subscriptions) {
        this(sessionRepository, assessmentService, sessionCodeGenerator, access, subscriptions, null);
    }

    @Autowired
    public SessionCreationService(ExamSessionRepository sessionRepository,
            AssessmentService assessmentService,
            SessionCodeGenerator sessionCodeGenerator,
            SessionAccess access,
            SessionSubscriptionRules subscriptions,
            TenancyService tenancyService) {
        this.sessionRepository = sessionRepository;
        this.assessmentService = assessmentService;
        this.sessionCodeGenerator = sessionCodeGenerator;
        this.access = access;
        this.subscriptions = subscriptions;
        this.tenancyService = tenancyService;
    }

    @Transactional
    public SessionResponse create(CreateSessionRequest request, CurrentUser caller) {
        UUID tenantId = access.requireTenant(caller);
        assertTenantActive(tenantId);
        subscriptions.validateWindow(request.opensAt(), request.closesAt());

        SubscriptionView subscription = subscriptions.lockedActiveSubscription(request.subscriptionPublicId(), tenantId);
        if (request.capacity() == null) {
            throw new SessionCapacityRequiredException();
        }
        if (request.capacity() <= 0) {
            throw new SessionCapacityInvalidException();
        }
        subscriptions.validateSubscriptionWindowAndCapacity(subscription, request.opensAt(), request.closesAt(),
                request.capacity(), false);
        subscriptions.rejectOverlap(subscription.publicId(), request.opensAt(), request.closesAt(), null);

        // Generates a fresh random exam from the question bank and publishes it —
        // assessment's own InsufficientQuestionBankException/InvalidSkillSelectionException
        // propagate unmodified if generation fails, before any ExamSession is saved.
        SnapshotResponse snapshot = assessmentService.generateAndPublish(request.name(), request.skills(), caller);

        ExamSession session = new ExamSession();
        session.setName(request.name());
        session.setTenantId(tenantId);
        session.setSubscriptionId(subscription.publicId());
        session.setLicenseKey(subscription.licenseKey());
        session.setExamVersionPublicId(snapshot.publicId());
        session.setExamDefinitionPublicId(snapshot.examDefinitionPublicId());
        session.setExamDefinitionVersion(snapshot.examDefinitionVersion());
        session.setOpensAt(request.opensAt());
        session.setClosesAt(request.closesAt());
        session.setCapacity(request.capacity());
        ExamMode mode = request.examMode() != null ? request.examMode() : ExamMode.OFFICIAL_EXAM;
        session.setExamMode(mode);
        session.setReusePolicy(com.pte.session.domain.enums.ReusePolicy.ALLOW);
        ExamPolicy policy = ExamPolicy.realExamDefault();
        policy.setLockdownMode(SessionPolicyResolver.resolveForCreate(request.lockdownMode()));
        session.setPolicy(policy);
        session.setSessionCode(sessionCodeGenerator.generate(tenantId, request.opensAt()));

        try {
            ExamSession saved = sessionRepository.save(session);
            sessionRepository.flush();
            return SessionMapper.toResponse(saved);
        } catch (DataIntegrityViolationException ex) {
            throw subscriptions.translateOverlap(ex);
        }
    }

    private void assertTenantActive(UUID tenantId) {
        if (tenancyService != null) {
            tenancyService.assertTenantActive(tenantId);
        }
    }
}
