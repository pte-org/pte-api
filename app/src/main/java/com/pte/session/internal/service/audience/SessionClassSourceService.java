package com.pte.session.internal.service.audience;

import com.pte.session.internal.service.SessionLifecycleService;
import com.pte.session.internal.service.enrollment.EnrollmentService;
import com.pte.enrollment.EnrollmentModuleService;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ExamSessionAudienceSource;
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.session.domain.enums.AudienceSourceType;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.dto.request.AudienceSourceRequest;
import com.pte.session.internal.dto.request.BulkEnrollRequest;
import com.pte.session.internal.dto.response.audience.SessionClassAssignmentResponse;
import com.pte.session.internal.exception.audience.ClassAssignmentNotAllowedException;
import com.pte.session.internal.exception.audience.ClassAssignmentNotFoundException;
import com.pte.session.internal.repository.ExamRegistrationRepository;
import com.pte.session.internal.repository.ExamSessionAudienceSourceRepository;
import com.pte.shared.StartedAttemptLookup;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Assigns whole classes to a session. A class is an audience source of type {@code CLASS}: on a draft the
 * source alone is recorded and resolved at preflight; on a scheduled exam the class's current students are
 * enrolled right away and the source is kept so the class can be unassigned again.
 */
@Service
public class SessionClassSourceService {

    private final SessionLifecycleService sessionLifecycleService;
    private final EnrollmentService enrollmentService;
    private final EnrollmentModuleService enrollmentModuleService;
    private final ExamSessionAudienceSourceRepository sourceRepository;
    private final ExamRegistrationRepository registrationRepository;
    private final ExamAudienceService audienceService;
    private final AudienceResolver resolver;
    private final StartedAttemptLookup startedAttemptLookup;

    public SessionClassSourceService(SessionLifecycleService sessionLifecycleService,
            EnrollmentService enrollmentService, EnrollmentModuleService enrollmentModuleService,
            ExamSessionAudienceSourceRepository sourceRepository,
            ExamRegistrationRepository registrationRepository, ExamAudienceService audienceService,
            AudienceResolver resolver, StartedAttemptLookup startedAttemptLookup) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.enrollmentService = enrollmentService;
        this.enrollmentModuleService = enrollmentModuleService;
        this.sourceRepository = sourceRepository;
        this.registrationRepository = registrationRepository;
        this.audienceService = audienceService;
        this.resolver = resolver;
        this.startedAttemptLookup = startedAttemptLookup;
    }

    /**
     * On a scheduled exam the class's current membership is always forwarded to {@code bulkEnroll}, even when
     * the class was assigned before, so members who joined since are enrolled too; {@code bulkEnroll} skips
     * students who are already enrolled.
     */
    @Transactional
    public SessionClassAssignmentResponse assign(UUID sessionPublicId, UUID classPublicId, CurrentUser caller) {
        ExamSession session = requireAssignable(sessionPublicId, caller);
        if (session.getStatus() == SessionStatus.DRAFT) {
            audienceService.addSource(sessionPublicId,
                    new AudienceSourceRequest(AudienceSourceType.CLASS, classPublicId), caller);
            return new SessionClassAssignmentResponse(sessionPublicId, classPublicId);
        }
        enrollmentService.bulkEnroll(sessionPublicId, new BulkEnrollRequest(
                enrollmentModuleService.findActiveStudentPublicIds(caller.tenantId(), classPublicId)), caller);
        if (sourceRepository.findBySessionIdAndSourceTypeAndSourcePublicId(session.getId(),
                AudienceSourceType.CLASS, classPublicId).isEmpty()) {
            ExamSessionAudienceSource source = new ExamSessionAudienceSource();
            source.setSession(session);
            source.setTenantId(caller.tenantId());
            source.setSourceType(AudienceSourceType.CLASS);
            source.setSourcePublicId(classPublicId);
            source.setCreatedBy(caller.userId());
            sourceRepository.save(source);
        }
        return new SessionClassAssignmentResponse(sessionPublicId, classPublicId);
    }

    @Transactional(readOnly = true)
    public List<SessionClassAssignmentResponse> list(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwned(sessionPublicId, caller);
        return sourceRepository.findBySessionIdAndSourceTypeOrderByCreatedAtAsc(session.getId(),
                        AudienceSourceType.CLASS).stream()
                .map(source -> new SessionClassAssignmentResponse(sessionPublicId, source.getSourcePublicId()))
                .toList();
    }

    /**
     * On a scheduled exam only the class's current members lose their enrollment: a student who has since
     * moved to another class is no longer found for this class and keeps the enrollment they got under it.
     */
    @Transactional
    public void unassign(UUID sessionPublicId, UUID classPublicId, CurrentUser caller) {
        ExamSession session = requireAssignable(sessionPublicId, caller);
        ExamSessionAudienceSource source = sourceRepository
                .findBySessionIdAndSourceTypeAndSourcePublicId(session.getId(), AudienceSourceType.CLASS,
                        classPublicId)
                .orElseThrow(ClassAssignmentNotFoundException::new);
        if (session.getStatus() == SessionStatus.DRAFT) {
            audienceService.removeSource(sessionPublicId, source.getPublicId(), caller);
            return;
        }
        List<UUID> removable = removableMembers(session, source, classPublicId, caller);
        if (!removable.isEmpty()) {
            registrationRepository.deleteBySessionIdAndStudentPublicIdInAndStatus(session.getId(), removable,
                    AudienceMemberStatus.ENROLLED);
        }
        sourceRepository.delete(source);
    }

    /**
     * Members of the class who may lose their seat: not still covered by another audience source of the
     * session, and without an attempt already started (those keep their enrollment, as in a manual unenroll).
     */
    private List<UUID> removableMembers(ExamSession session, ExamSessionAudienceSource removed, UUID classPublicId,
            CurrentUser caller) {
        Set<UUID> stillCovered = new HashSet<>();
        sourceRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .filter(other -> !other.getPublicId().equals(removed.getPublicId()))
                .forEach(other -> stillCovered.addAll(resolver.resolveSource(other, caller.tenantId())));
        List<UUID> candidates = enrollmentModuleService.findActiveStudentPublicIds(caller.tenantId(), classPublicId)
                .stream().filter(student -> !stillCovered.contains(student)).toList();
        if (candidates.isEmpty()) {
            return candidates;
        }
        Set<UUID> started = startedAttemptLookup.findStartedStudentPublicIds(caller.tenantId(),
                List.of(session.getPublicId()), candidates);
        return candidates.stream().filter(student -> !started.contains(student)).toList();
    }

    private ExamSession requireAssignable(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller);
        if (session.getStatus() != SessionStatus.SCHEDULED && session.getStatus() != SessionStatus.DRAFT) {
            throw new ClassAssignmentNotAllowedException();
        }
        return session;
    }
}
