package com.pte.session.internal.service.lifecycle;

import com.pte.billing.BillingService;
import com.pte.billing.SubscriptionView;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.dto.request.ChangeSubscriptionRequest;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.exception.PolicyLockedException;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.exception.SessionSubscriptionConflictException;
import com.pte.session.internal.exception.SessionSubscriptionNotFoundException;
import com.pte.session.internal.mapper.SessionMapper;
import com.pte.session.internal.repository.EnrollmentRepository;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Rebinds a scheduled session and its enrollments with subscription-before-session locking. */
@Service
public class SessionSubscriptionService {

    private final ExamSessionRepository sessionRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final BillingService billingService;
    private final SessionAccess access;
    private final SessionSubscriptionRules subscriptions;

    public SessionSubscriptionService(ExamSessionRepository sessionRepository,
            EnrollmentRepository enrollmentRepository,
            BillingService billingService,
            SessionAccess access,
            SessionSubscriptionRules subscriptions) {
        this.sessionRepository = sessionRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.billingService = billingService;
        this.access = access;
        this.subscriptions = subscriptions;
    }

    @Transactional
    public SessionResponse changeSubscription(UUID publicId, ChangeSubscriptionRequest request, CurrentUser caller) {
        UUID tenantId = access.requireTenant(caller);
        ExamSession observed = access.observedSession(publicId, tenantId);
        if (observed.getStatus() != SessionStatus.SCHEDULED) {
            throw new PolicyLockedException();
        }

        UUID oldSubscriptionId = observed.getSubscriptionId();
        UUID targetSubscriptionId = request.subscriptionPublicId();
        if (oldSubscriptionId == null || targetSubscriptionId == null) {
            throw new SessionSubscriptionNotFoundException();
        }
        List<UUID> lockOrder = List.of(oldSubscriptionId, targetSubscriptionId).stream()
                .sorted(Comparator.naturalOrder()).toList();
        List<SubscriptionView> lockedSubscriptions = billingService.lockSubscriptions(lockOrder, tenantId);
        SubscriptionView target = lockedSubscriptions.stream()
                .filter(subscription -> subscription.publicId().equals(targetSubscriptionId))
                .findFirst()
                .orElseThrow(SessionSubscriptionNotFoundException::new);
        lockedSubscriptions.stream()
                .filter(subscription -> subscription.publicId().equals(oldSubscriptionId))
                .findFirst()
                .orElseThrow(SessionSubscriptionNotFoundException::new);

        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
        if (session.getStatus() != SessionStatus.SCHEDULED
                || !oldSubscriptionId.equals(session.getSubscriptionId())) {
            throw new SessionSubscriptionConflictException();
        }

        long enrollmentCount = enrollmentRepository.countBySessionId(session.getId());
        subscriptions.validateSubscriptionWindowAndCapacity(target, session.getOpensAt(), session.getClosesAt(),
                enrollmentCount, true);
        subscriptions.rejectOverlap(target.publicId(), session.getOpensAt(), session.getClosesAt(), session.getPublicId());

        session.setSubscriptionId(target.publicId());
        session.setLicenseKey(target.licenseKey());
        session.setCapacity(Math.min(session.getCapacity(), target.maxStudentsPerSession()));
        enrollmentRepository.findBySessionId(session.getId())
                .forEach(enrollment -> enrollment.setLicenseKey(target.licenseKey()));

        try {
            sessionRepository.flush();
            return SessionMapper.toResponse(session);
        } catch (DataIntegrityViolationException ex) {
            throw subscriptions.translateOverlap(ex);
        }
    }
}
