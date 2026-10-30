package com.pte.session.internal.service.enrollment;

import com.pte.session.internal.service.SessionLifecycleService;
import com.pte.identity.IdentityService;
import com.pte.session.domain.ExamRegistration;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.AudienceMemberStatus;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.event.StudentEnrolledEvent;
import com.pte.session.dto.event.StudentEnrollmentConflictEvent;
import com.pte.session.dto.response.SlotConflict;
import com.pte.session.internal.dto.request.BulkEnrollRequest;
import com.pte.session.internal.dto.request.EnrollStudentRequest;
import com.pte.session.internal.dto.response.enrollment.BulkEnrollResponse;
import com.pte.session.internal.dto.response.enrollment.EnrollmentResponse;
import com.pte.session.internal.dto.response.enrollment.StudentEnrollmentResponse;
import com.pte.session.internal.exception.enrollment.AlreadyEnrolledException;
import com.pte.session.internal.exception.enrollment.EnrolledStudentAlreadyStartedException;
import com.pte.session.internal.exception.enrollment.EnrollmentNotFoundException;
import com.pte.session.internal.exception.audience.ExamAudienceLockedException;
import com.pte.session.internal.exception.capacity.SessionCapacityExceededException;
import com.pte.session.internal.exception.capacity.SessionTimeConflictException;
import com.pte.session.internal.repository.ExamRegistrationRepository;
import com.pte.shared.StartedAttemptLookup;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manual enrollment: moves a student's {@link ExamRegistration} to {@code ENROLLED}, creating the row when the
 * student was never part of the audience. A double enrollment is rejected by the DB unique constraint on
 * (session, student), not by a check-then-act race.
 */
@Service
public class EnrollmentService {

    private final SessionLifecycleService sessionLifecycleService;
    private final ExamRegistrationRepository registrationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TenancyService tenancyService;
    private final IdentityService identityService;
    private final StartedAttemptLookup startedAttemptLookup;
    private final Clock clock;

    public EnrollmentService(SessionLifecycleService sessionLifecycleService,
            ExamRegistrationRepository registrationRepository, ApplicationEventPublisher eventPublisher,
            TenancyService tenancyService, IdentityService identityService,
            StartedAttemptLookup startedAttemptLookup,
            Clock clock) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.registrationRepository = registrationRepository;
        this.eventPublisher = eventPublisher;
        this.tenancyService = tenancyService;
        this.identityService = identityService;
        this.startedAttemptLookup = startedAttemptLookup;
        this.clock = clock;
    }

    @Transactional
    public EnrollmentResponse enrollStudent(UUID sessionPublicId, EnrollStudentRequest request, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller);
        tenancyService.assertTenantActive(session.getTenantId());
        requireManualAudienceMutationAllowed(session);
        UUID studentPublicId = request.studentPublicId();
        identityService.requireActiveStudentInTenant(studentPublicId, session.getTenantId());
        identityService.lockActiveStudentsInTenant(List.of(studentPublicId), session.getTenantId());
        ExamRegistration existing = registrationRepository
                .findBySessionIdAndStudentPublicIdIn(session.getId(), List.of(studentPublicId)).stream()
                .findFirst().orElse(null);
        if (existing != null && existing.isEnrolled()) {
            throw new AlreadyEnrolledException();
        }
        List<SlotConflict> conflicts = findConflicts(session, List.of(studentPublicId));
        if (!conflicts.isEmpty()) {
            throw new SessionTimeConflictException(conflicts.get(0).conflictingSessionPublicId(),
                    conflicts.get(0).conflictingSessionName());
        }
        if (session.getCapacity() != null && enrolledCount(session) >= session.getCapacity()) {
            throw new SessionCapacityExceededException();
        }
        ExamRegistration saved = save(enroll(session, existing, studentPublicId, clock.instant()));
        publishEnrolled(session, studentPublicId);
        return new EnrollmentResponse(saved.getPublicId(), session.getPublicId(), studentPublicId);
    }

    /**
     * Bulk-enrolls students not already enrolled (already-enrolled ids are reported, not errors); duplicate
     * ids in the request are deduped. The session is fetched under a pessimistic write lock, held for the
     * whole method, so the capacity check-then-insert cannot race a concurrent call against the same session.
     */
    @Transactional
    public BulkEnrollResponse bulkEnroll(UUID sessionPublicId, BulkEnrollRequest request, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller);
        tenancyService.assertTenantActive(session.getTenantId());
        requireManualAudienceMutationAllowed(session);
        identityService.requireActiveStudentsInTenant(request.studentPublicIds(), session.getTenantId());
        return bulkEnrollLocked(session, request);
    }

    /** Canonical publish path: materializes the already generated audience before scheduling. */
    @Transactional
    public BulkEnrollResponse bulkEnrollPublishedExam(ExamSession session, BulkEnrollRequest request) {
        tenancyService.assertTenantActive(session.getTenantId());
        identityService.requireActiveStudentsInTenant(request.studentPublicIds(), session.getTenantId());
        if (session.getStatus() != SessionStatus.READY || session.getExamVersionPublicId() == null) {
            throw new ExamAudienceLockedException();
        }
        return bulkEnrollLocked(session, request);
    }

    private BulkEnrollResponse bulkEnrollLocked(ExamSession session, BulkEnrollRequest request) {
        identityService.lockActiveStudentsInTenant(request.studentPublicIds(), session.getTenantId());
        Map<UUID, ExamRegistration> existingByStudent = registrationRepository
                .findBySessionIdAndStudentPublicIdIn(session.getId(), request.studentPublicIds()).stream()
                .collect(Collectors.toMap(ExamRegistration::getStudentPublicId, Function.identity()));
        Set<UUID> alreadyEnrolled = existingByStudent.values().stream().filter(ExamRegistration::isEnrolled)
                .map(ExamRegistration::getStudentPublicId).collect(Collectors.toCollection(LinkedHashSet::new));

        List<UUID> candidates = new ArrayList<>();
        Set<UUID> seenInBatch = new HashSet<>();
        for (UUID studentPublicId : request.studentPublicIds()) {
            if (!alreadyEnrolled.contains(studentPublicId) && seenInBatch.add(studentPublicId)) {
                candidates.add(studentPublicId);
            }
        }
        List<SlotConflict> skippedByConflict = findConflicts(session, candidates);
        Set<UUID> conflicted = skippedByConflict.stream().map(SlotConflict::studentPublicId)
                .collect(Collectors.toSet());
        // Counted before the registrations below are promoted, so the pending changes cannot inflate it.
        long enrolledBefore = session.getCapacity() == null ? 0L : enrolledCount(session);
        Instant now = clock.instant();
        List<ExamRegistration> toEnroll = candidates.stream().filter(student -> !conflicted.contains(student))
                .map(student -> enroll(session, existingByStudent.get(student), student, now)).toList();

        // Fail closed, before any write: a partial silent enroll would be worse than a clear rejection.
        if (session.getCapacity() != null && enrolledBefore + toEnroll.size() > session.getCapacity()) {
            throw new SessionCapacityExceededException();
        }
        List<ExamRegistration> saved;
        try {
            saved = registrationRepository.saveAll(toEnroll);
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyEnrolledException();
        }
        saved.forEach(registration -> publishEnrolled(session, registration.getStudentPublicId()));
        if (!skippedByConflict.isEmpty()) {
            eventPublisher.publishEvent(new StudentEnrollmentConflictEvent(session.getPublicId(),
                    session.getTenantId(), session.getName(), skippedByConflict));
        }
        return new BulkEnrollResponse(saved.stream().map(ExamRegistration::getStudentPublicId).toList(),
                List.copyOf(alreadyEnrolled), skippedByConflict);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> list(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwned(sessionPublicId, caller);
        return registrationRepository.findBySessionIdAndStatus(session.getId(), AudienceMemberStatus.ENROLLED)
                .stream()
                .map(registration -> new EnrollmentResponse(registration.getPublicId(), session.getPublicId(),
                        registration.getStudentPublicId()))
                .toList();
    }

    /** Removes the student from the roster; the attendance history stays in the audit trail. */
    @Transactional
    public void unenroll(UUID sessionPublicId, UUID enrollmentPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller);
        requireManualAudienceMutationAllowed(session);
        ExamRegistration registration = registrationRepository.findByPublicId(enrollmentPublicId)
                .filter(ExamRegistration::isEnrolled)
                .orElseThrow(EnrollmentNotFoundException::new);
        if (!registration.getSession().getId().equals(session.getId())) {
            throw new EnrollmentNotFoundException();
        }
        if (!startedAttemptLookup.findStartedStudentPublicIds(session.getTenantId(), List.of(session.getPublicId()),
                List.of(registration.getStudentPublicId())).isEmpty()) {
            throw new EnrolledStudentAlreadyStartedException();
        }
        registrationRepository.delete(registration);
    }

    /** Backs the enrollment pending-exam-request transfer warning: one join-fetch query, no per-row session lookup. */
    @Transactional(readOnly = true)
    public List<StudentEnrollmentResponse> listForStudent(UUID studentPublicId, CurrentUser caller) {
        return registrationRepository.findEnrollmentsOfStudent(studentPublicId, caller.tenantId()).stream()
                .map(registration -> new StudentEnrollmentResponse(
                        registration.getPublicId(),
                        registration.getSession().getPublicId(),
                        registration.getSession().getName(),
                        registration.getSession().getStatus().name(),
                        registration.getSession().getOpensAt(),
                        registration.getSession().getClosesAt()))
                .toList();
    }

    private ExamRegistration enroll(ExamSession session, ExamRegistration existing, UUID studentPublicId,
            Instant now) {
        ExamRegistration registration = existing != null ? existing : new ExamRegistration();
        registration.setSession(session);
        registration.setStudentPublicId(studentPublicId);
        registration.setTenantId(session.getTenantId());
        registration.setLicenseKey(session.getLicenseKey());
        registration.setStatus(AudienceMemberStatus.ENROLLED);
        registration.setReason(null);
        registration.setPriorSessionPublicId(null);
        registration.setPriorSessionName(null);
        registration.setPriorStatus(null);
        registration.setPublishedAt(now);
        return registration;
    }

    private ExamRegistration save(ExamRegistration registration) {
        try {
            return registrationRepository.save(registration);
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyEnrolledException();
        }
    }

    private long enrolledCount(ExamSession session) {
        return registrationRepository.countBySessionIdAndStatus(session.getId(), AudienceMemberStatus.ENROLLED);
    }

    private List<SlotConflict> findConflicts(ExamSession session, List<UUID> studentPublicIds) {
        if (studentPublicIds.isEmpty()) {
            return List.of();
        }
        return registrationRepository.findConflictingEnrollments(session.getTenantId(), studentPublicIds,
                        session.getOpensAt(), session.getClosesAt()).stream()
                .map(conflict -> new SlotConflict(conflict.getStudentPublicId(),
                        conflict.getConflictingSessionPublicId(), conflict.getConflictingSessionName()))
                .toList();
    }

    /**
     * The roster is editable once the exam is published and before it runs (SCHEDULED) or after it ends
     * (CLOSED). Earlier statuses take their audience from the wizard's sources, and OPEN exams have students
     * sitting them.
     */
    private void requireManualAudienceMutationAllowed(ExamSession session) {
        if (session.getStatus() != SessionStatus.SCHEDULED && session.getStatus() != SessionStatus.CLOSED) {
            throw new ExamAudienceLockedException();
        }
    }

    /** Skipped for closed exams: an invitation to an exam that already ended would only confuse the student. */
    private void publishEnrolled(ExamSession session, UUID studentPublicId) {
        if (session.getStatus() == SessionStatus.CLOSED) {
            return;
        }
        eventPublisher.publishEvent(
                new StudentEnrolledEvent(session.getPublicId(), studentPublicId, session.getTenantId()));
    }
}
