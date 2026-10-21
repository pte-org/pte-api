package com.pte.session.internal.service.lifecycle;

import com.pte.billing.SubscriptionView;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.constant.SessionConstants;
import com.pte.session.internal.dto.request.PatchExamScheduleRequest;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.internal.exception.ExamCloseTimeNotExtendedException;
import com.pte.session.internal.exception.ExamDraftConfigurationException;
import com.pte.session.internal.exception.ExamScheduleNotEditableException;
import com.pte.session.internal.exception.ExamScheduleVersionConflictException;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.mapper.SessionMapper;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Owns the narrow schedule-edit boundary after an exam leaves DRAFT.
 * Scheduled exams can update identity and timing; open exams can only extend
 * their close time so an emergency adjustment cannot change the delivered
 * snapshot or audience while students are working.
 */
@Service
public class SessionScheduleService {

    private final ExamSessionRepository sessionRepository;
    private final SessionAccess access;
    private final SessionSubscriptionRules subscriptions;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final TenancyService tenancyService;

    public SessionScheduleService(ExamSessionRepository sessionRepository,
            SessionAccess access,
            SessionSubscriptionRules subscriptions,
            AuditLogService auditLogService,
            Clock clock) {
        this(sessionRepository, access, subscriptions, auditLogService, clock, null);
    }

    @Autowired
    public SessionScheduleService(ExamSessionRepository sessionRepository,
            SessionAccess access,
            SessionSubscriptionRules subscriptions,
            AuditLogService auditLogService,
            Clock clock,
            TenancyService tenancyService) {
        this.sessionRepository = sessionRepository;
        this.access = access;
        this.subscriptions = subscriptions;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.tenancyService = tenancyService;
    }

    @Transactional
    public SessionResponse patchSchedule(UUID publicId, PatchExamScheduleRequest request, CurrentUser caller) {
        UUID tenantId = access.requireTenant(caller);
        assertTenantActive(tenantId);
        ExamSession session = sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
        if (request.expectedVersion() != null
                && !request.expectedVersion().equals(session.getDraftVersion())) {
            throw new ExamScheduleVersionConflictException();
        }
        if (session.getStatus() == SessionStatus.OPEN) {
            return extendOpenSession(session, request, caller);
        }
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new ExamScheduleNotEditableException();
        }
        Instant now = clock.instant();
        if (!session.getOpensAt().isAfter(now)) {
            return promoteDueScheduledSession(session, request, caller, now);
        }

        String name = request.name() == null ? session.getName() : request.name().trim();
        if (name.isBlank()) {
            throw new ExamDraftConfigurationException(SessionConstants.SESSION_NAME_REQUIRED);
        }
        Instant opensAt = request.opensAt() == null ? session.getOpensAt() : request.opensAt();
        Instant closesAt = request.closesAt() == null ? session.getClosesAt() : request.closesAt();
        subscriptions.validateWindow(opensAt, closesAt);
        SubscriptionView subscription = subscriptions.lockedActiveSubscription(
                session.getSubscriptionId(), session.getTenantId());
        subscriptions.validateSubscriptionWindowAndCapacity(subscription, opensAt, closesAt,
                session.getCapacity(), false);
        subscriptions.rejectOverlap(session.getSubscriptionId(), opensAt, closesAt, session.getPublicId());

        boolean changed = !Objects.equals(session.getName(), name)
                || !Objects.equals(session.getOpensAt(), opensAt)
                || !Objects.equals(session.getClosesAt(), closesAt);
        if (!changed) {
            return SessionMapper.toResponse(session);
        }
        session.setName(name);
        session.setOpensAt(opensAt);
        session.setClosesAt(closesAt);
        ExamSession saved = sessionRepository.saveAndFlush(session);
        recordExamAudit(caller, saved, SessionConstants.EVENT_EXAM_SCHEDULE_UPDATED, saved.getName());
        return SessionMapper.toResponse(saved);
    }

    private SessionResponse promoteDueScheduledSession(ExamSession session,
            PatchExamScheduleRequest request, CurrentUser caller, Instant now) {
        if ((request.name() != null && !Objects.equals(request.name().trim(), session.getName()))
                || (request.opensAt() != null && !Objects.equals(request.opensAt(), session.getOpensAt()))
                || request.closesAt() == null) {
            throw new ExamScheduleNotEditableException();
        }
        Instant closesAt = request.closesAt();
        if (!closesAt.isAfter(now)) {
            throw new ExamScheduleNotEditableException();
        }
        subscriptions.validateWindow(session.getOpensAt(), closesAt);
        SubscriptionView subscription = subscriptions.lockedActiveSubscription(
                session.getSubscriptionId(), session.getTenantId());
        subscriptions.validateSubscriptionWindowAndCapacity(subscription, session.getOpensAt(), closesAt,
                session.getCapacity(), false);
        subscriptions.rejectOverlap(session.getSubscriptionId(), session.getOpensAt(), closesAt,
                session.getPublicId());
        if (closesAt.isBefore(session.getClosesAt())) {
            throw new ExamCloseTimeNotExtendedException();
        }

        session.open();
        recordExamAudit(systemCaller(session), session, SessionConstants.EVENT_EXAM_AUTO_OPENED,
                "%s (%s -> %s)".formatted(session.getName(), SessionStatus.SCHEDULED, SessionStatus.OPEN));
        boolean extended = closesAt.isAfter(session.getClosesAt());
        if (extended) {
            session.setClosesAt(closesAt);
        }
        ExamSession saved = sessionRepository.saveAndFlush(session);
        if (extended) {
            recordExamAudit(caller, saved, SessionConstants.EVENT_EXAM_SCHEDULE_UPDATED, saved.getName());
        }
        return SessionMapper.toResponse(saved);
    }

    private SessionResponse extendOpenSession(ExamSession session,
            PatchExamScheduleRequest request, CurrentUser caller) {
        if ((request.name() != null && !Objects.equals(request.name().trim(), session.getName()))
                || (request.opensAt() != null && !Objects.equals(request.opensAt(), session.getOpensAt()))
                || request.closesAt() == null) {
            throw new ExamScheduleNotEditableException();
        }
        Instant now = clock.instant();
        if (!request.closesAt().isAfter(session.getClosesAt()) || !request.closesAt().isAfter(now)) {
            throw new ExamCloseTimeNotExtendedException();
        }
        subscriptions.validateWindow(session.getOpensAt(), request.closesAt());
        SubscriptionView subscription = subscriptions.lockedActiveSubscription(
                session.getSubscriptionId(), session.getTenantId());
        subscriptions.validateSubscriptionWindowAndCapacity(subscription, session.getOpensAt(),
                request.closesAt(), session.getCapacity(), false);
        subscriptions.rejectOverlap(session.getSubscriptionId(), session.getOpensAt(), request.closesAt(),
                session.getPublicId());
        session.setClosesAt(request.closesAt());
        ExamSession saved = sessionRepository.saveAndFlush(session);
        recordExamAudit(caller, saved, SessionConstants.EVENT_EXAM_SCHEDULE_UPDATED, saved.getName());
        return SessionMapper.toResponse(saved);
    }

    private CurrentUser systemCaller(ExamSession session) {
        return new CurrentUser(SessionConstants.SYSTEM_ACTOR_USER_ID, session.getTenantId(), List.of("SYSTEM"));
    }

    private void assertTenantActive(UUID tenantId) {
        if (tenancyService != null) {
            tenancyService.assertTenantActive(tenantId);
        }
    }

    private void recordExamAudit(CurrentUser caller, ExamSession session, String action, String summary) {
        auditLogService.record(caller, SessionConstants.AGGREGATE_EXAM_SESSION,
                session.getPublicId().toString(), session.getName(), session.getSessionCode(), action, summary);
    }
}
