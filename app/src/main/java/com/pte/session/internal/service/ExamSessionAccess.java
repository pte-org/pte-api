package com.pte.session.internal.service;

import com.pte.billing.BillingService;
import com.pte.billing.SubscriptionView;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.exception.draft.ExamDraftNotEditableException;
import com.pte.session.internal.exception.staff.HostContextRequiredException;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.exception.capacity.SessionSubscriptionNotFoundException;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.audit.AuditLogService;
import com.pte.tenancy.TenancyService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Tenant-scoped session lookups, subscription locking and audit helpers shared by the exam workflow services. */
@Component
public class ExamSessionAccess {

    private final ExamSessionRepository sessionRepository;
    private final SessionLifecycleService sessionLifecycleService;
    private final BillingService billingService;
    private final TenancyService tenancyService;
    private final AuditLogService auditLogService;

    ExamSessionAccess(ExamSessionRepository sessionRepository,
            SessionLifecycleService sessionLifecycleService,
            BillingService billingService,
            TenancyService tenancyService,
            AuditLogService auditLogService) {
        this.sessionRepository = sessionRepository;
        this.sessionLifecycleService = sessionLifecycleService;
        this.billingService = billingService;
        this.tenancyService = tenancyService;
        this.auditLogService = auditLogService;
    }

    public ExamSession ownedForEdit(UUID publicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwnedWithLock(publicId, caller);
        if (session.getStatus() != SessionStatus.DRAFT) throw new ExamDraftNotEditableException();
        return session;
    }

    public ExamSession owned(UUID publicId, CurrentUser caller) {
        return sessionRepository.findByPublicIdAndTenantId(publicId, requireTenant(caller))
                .orElseThrow(SessionNotFoundException::new);
    }

    public SubscriptionView lockedActiveSubscription(UUID publicId, UUID tenantId) {
        if (publicId == null || tenantId == null) {
            throw new SessionSubscriptionNotFoundException();
        }
        List<SubscriptionView> locked = billingService.lockSubscriptions(List.of(publicId), tenantId);
        if (!locked.isEmpty()) {
            return locked.get(0);
        }
        return billingService.getActiveSubscription(publicId, tenantId)
                .orElseThrow(SessionSubscriptionNotFoundException::new);
    }

    public SubscriptionView exactOrActiveSubscription(UUID publicId, UUID tenantId) {
        return billingService.getSubscription(publicId, tenantId)
                .or(() -> billingService.getActiveSubscription(publicId, tenantId))
                .orElseThrow(SessionSubscriptionNotFoundException::new);
    }

    public ExamSession observedSession(UUID publicId, CurrentUser caller) {
        return sessionRepository.findByPublicIdAndTenantId(publicId, caller.tenantId())
                .orElseGet(() -> sessionLifecycleService.findOwnedWithLock(publicId, caller));
    }

    public ExamSession lockedSession(UUID publicId, CurrentUser caller, UUID tenantId) {
        return sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseGet(() -> sessionLifecycleService.findOwnedWithLock(publicId, caller));
    }

    public UUID requireTenant(CurrentUser caller) {
        if (caller == null || caller.tenantId() == null) throw new HostContextRequiredException();
        return caller.tenantId();
    }

    public void assertTenantActive(UUID tenantId) {
        if (tenancyService != null) {
            tenancyService.assertTenantActive(tenantId);
        }
    }

    public void recordExamAudit(CurrentUser caller, ExamSession session, String action, String summary) {
        auditLogService.record(caller, SessionConstants.AGGREGATE_EXAM_SESSION,
                session.getPublicId().toString(), session.getName(), session.getSessionCode(), action, summary);
    }
}
