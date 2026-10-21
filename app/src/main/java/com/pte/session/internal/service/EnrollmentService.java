package com.pte.session.internal.service;

import com.pte.session.domain.Enrollment;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ProctorAssignment;
import com.pte.session.domain.enums.ProctorRole;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.dto.event.StudentEnrolledEvent;
import com.pte.session.dto.event.StudentEnrollmentConflictEvent;
import com.pte.session.dto.response.SlotConflict;
import com.pte.session.internal.dto.request.AssignProctorRequest;
import com.pte.session.internal.dto.request.BulkEnrollRequest;
import com.pte.session.internal.dto.request.EnrollStudentRequest;
import com.pte.session.internal.dto.request.UpdateProctorRoleRequest;
import com.pte.session.internal.dto.response.BulkEnrollResponse;
import com.pte.session.internal.dto.response.EnrollmentResponse;
import com.pte.session.internal.dto.response.ProctorAssignmentResponse;
import com.pte.session.internal.dto.response.StudentEnrollmentResponse;
import com.pte.session.internal.exception.AlreadyAssignedException;
import com.pte.session.internal.exception.AlreadyEnrolledException;
import com.pte.session.internal.exception.EnrolledStudentAlreadyStartedException;
import com.pte.session.internal.exception.EnrollmentNotFoundException;
import com.pte.session.internal.exception.ExamAudienceLockedException;
import com.pte.session.internal.exception.ProctorAssignmentNotFoundException;
import com.pte.session.internal.exception.SessionCapacityExceededException;
import com.pte.session.internal.exception.SessionTimeConflictException;
import com.pte.session.internal.repository.EnrollmentRepository;
import com.pte.session.internal.repository.ProctorAssignmentRepository;
import com.pte.identity.IdentityService;
import com.pte.shared.StartedAttemptLookup;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Manual student enrollment + proctor assignment (Milestone 1 scope — no file
 * import). Double-enroll/assign is guarded by a DB unique constraint, not a
 * check-then-act race.
 */
@Service
public class EnrollmentService {

    private final SessionLifecycleService sessionLifecycleService;
    private final EnrollmentRepository enrollmentRepository;
    private final ProctorAssignmentRepository proctorAssignmentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TenancyService tenancyService;
    private final IdentityService identityService;
    private final ExamFormProvisioningService formProvisioningService;
    private final StartedAttemptLookup startedAttemptLookup;

    public EnrollmentService(SessionLifecycleService sessionLifecycleService, EnrollmentRepository enrollmentRepository,
                             ProctorAssignmentRepository proctorAssignmentRepository,
                             ApplicationEventPublisher eventPublisher) {
        this(sessionLifecycleService, enrollmentRepository, proctorAssignmentRepository, eventPublisher, null, null);
    }

    public EnrollmentService(SessionLifecycleService sessionLifecycleService, EnrollmentRepository enrollmentRepository,
                             ProctorAssignmentRepository proctorAssignmentRepository,
                             ApplicationEventPublisher eventPublisher, TenancyService tenancyService) {
        this(sessionLifecycleService, enrollmentRepository, proctorAssignmentRepository, eventPublisher,
                tenancyService, null);
    }

    public EnrollmentService(SessionLifecycleService sessionLifecycleService,
                             EnrollmentRepository enrollmentRepository,
                             ProctorAssignmentRepository proctorAssignmentRepository,
                             ApplicationEventPublisher eventPublisher, TenancyService tenancyService,
                             IdentityService identityService) {
        this(sessionLifecycleService, enrollmentRepository, proctorAssignmentRepository, eventPublisher,
                tenancyService, identityService, null, null);
    }

    @Autowired
    public EnrollmentService(SessionLifecycleService sessionLifecycleService, EnrollmentRepository enrollmentRepository,
                             ProctorAssignmentRepository proctorAssignmentRepository,
                             ApplicationEventPublisher eventPublisher, TenancyService tenancyService,
                             IdentityService identityService, ExamFormProvisioningService formProvisioningService,
                             StartedAttemptLookup startedAttemptLookup) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.enrollmentRepository = enrollmentRepository;
        this.proctorAssignmentRepository = proctorAssignmentRepository;
        this.eventPublisher = eventPublisher;
        this.tenancyService = tenancyService;
        this.identityService = identityService;
        this.formProvisioningService = formProvisioningService;
        this.startedAttemptLookup = startedAttemptLookup;
    }

    @Transactional
    public EnrollmentResponse enrollStudent(UUID sessionPublicId, EnrollStudentRequest request, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller);
        assertTenantActive(session);
        requireManualAudienceMutationAllowed(session);
        requireActiveStudent(request.studentPublicId(), session.getTenantId());
        lockActiveStudents(List.of(request.studentPublicId()), session.getTenantId());
        if (enrollmentRepository.existsBySessionIdAndStudentPublicId(session.getId(), request.studentPublicId())) {
            throw new AlreadyEnrolledException();
        }
        List<SlotConflict> conflicts = findConflicts(session, List.of(request.studentPublicId()));
        if (!conflicts.isEmpty()) {
            throw new SessionTimeConflictException(conflicts.get(0).conflictingSessionPublicId());
        }
        if (session.getCapacity() != null && enrollmentRepository.countBySessionId(session.getId()) >= session.getCapacity()) {
            throw new SessionCapacityExceededException();
        }
        Enrollment enrollment = new Enrollment();
        enrollment.setSession(session);
        enrollment.setStudentPublicId(request.studentPublicId());
        enrollment.setTenantId(session.getTenantId());
        enrollment.setLicenseKey(session.getLicenseKey());

        Enrollment saved = save(enrollment);
        formProvisioningService.provision(session, List.of(saved.getStudentPublicId()), caller);
        publishEnrolled(session, saved.getStudentPublicId());
        return new EnrollmentResponse(saved.getPublicId(), session.getPublicId(), saved.getStudentPublicId());
    }

    /**
     * Bulk-enrolls students not already on the roster (already-enrolled ids
     * are reported, not errors); intra-request duplicate ids are also
     * deduped. The {@code saveAll} catch guards the rare true concurrent-
     * double-enroll race, same rationale as {@link #enrollStudent}. It
     * depends on {@code Enrollment} using {@code GenerationType.IDENTITY}
     * (forces a synchronous flush inside {@code saveAll}) — re-verify this
     * guard if that id strategy ever changes.
     * <p>
     * Fetches the session under a pessimistic write lock — held for this
     * whole method — so the capacity check-then-insert below can't race a
     * concurrent {@code bulkEnroll} call against the same session (both would
     * otherwise read the same pre-write count and both commit, overshooting
     * {@code capacity}).
     */
    @Transactional
    public BulkEnrollResponse bulkEnroll(UUID sessionPublicId, BulkEnrollRequest request, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller);
        assertTenantActive(session);
        requireManualAudienceMutationAllowed(session);
        requireActiveStudents(request.studentPublicIds(), session.getTenantId());
        BulkEnrollResponse response = bulkEnrollLocked(session, request, caller);
        formProvisioningService.provision(session, response.enrolled(), caller);
        return response;
    }

    /** Canonical publish path: materializes the already generated audience before scheduling. */
    @Transactional
    BulkEnrollResponse bulkEnrollPublishedExam(ExamSession session, BulkEnrollRequest request, CurrentUser caller) {
        assertTenantActive(session);
        requireActiveStudents(request.studentPublicIds(), session.getTenantId());
        if (session.getStatus() != com.pte.session.domain.enums.SessionStatus.READY
                || session.getGenerationJobPublicId() == null) {
            throw new ExamAudienceLockedException();
        }
        return bulkEnrollLocked(session, request, caller);
    }

    private BulkEnrollResponse bulkEnrollLocked(ExamSession session, BulkEnrollRequest request, CurrentUser caller) {
        lockActiveStudents(request.studentPublicIds(), session.getTenantId());

        List<UUID> existing = enrollmentRepository
                .findBySessionIdAndStudentPublicIdIn(session.getId(), request.studentPublicIds())
                .stream().map(Enrollment::getStudentPublicId).toList();
        Set<UUID> alreadyEnrolled = new LinkedHashSet<>(existing);

        List<Enrollment> toCreate = new ArrayList<>();
        Set<UUID> seenInBatch = new HashSet<>();
        List<UUID> candidateStudentIds = new ArrayList<>();
        for (UUID studentPublicId : request.studentPublicIds()) {
            if (alreadyEnrolled.contains(studentPublicId) || !seenInBatch.add(studentPublicId)) {
                continue;
            }
            candidateStudentIds.add(studentPublicId);
        }

        List<SlotConflict> skippedByConflict = findConflicts(session, candidateStudentIds);
        Set<UUID> conflictStudentIds = skippedByConflict.stream()
                .map(SlotConflict::studentPublicId)
                .collect(java.util.stream.Collectors.toSet());
        for (UUID studentPublicId : candidateStudentIds) {
            if (conflictStudentIds.contains(studentPublicId)) {
                continue;
            }
            Enrollment enrollment = new Enrollment();
            enrollment.setSession(session);
            enrollment.setStudentPublicId(studentPublicId);
            enrollment.setTenantId(session.getTenantId());
            enrollment.setLicenseKey(session.getLicenseKey());
            toCreate.add(enrollment);
        }

        // Fail closed, before any write — a partial silent enroll would be
        // worse than a clear rejection the FE can react to with another
        // batch/session.
        if (session.getCapacity() != null) {
            long existingCount = enrollmentRepository.countBySessionId(session.getId());
            if (existingCount + toCreate.size() > session.getCapacity()) {
                throw new SessionCapacityExceededException();
            }
        }

        List<Enrollment> saved;
        try {
            saved = enrollmentRepository.saveAll(toCreate);
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyEnrolledException();
        }

        for (Enrollment enrollment : saved) {
            publishEnrolled(session, enrollment.getStudentPublicId());
        }
        if (!skippedByConflict.isEmpty()) {
            eventPublisher.publishEvent(new StudentEnrollmentConflictEvent(session.getPublicId(),
                    session.getTenantId(), session.getName(), skippedByConflict));
        }

        List<UUID> enrolled = saved.stream().map(Enrollment::getStudentPublicId).toList();
        return new BulkEnrollResponse(enrolled, List.copyOf(alreadyEnrolled), skippedByConflict);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> list(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwned(sessionPublicId, caller);
        return enrollmentRepository.findBySessionId(session.getId()).stream()
                .map(enrollment -> new EnrollmentResponse(enrollment.getPublicId(), session.getPublicId(),
                        enrollment.getStudentPublicId()))
                .toList();
    }

    @Transactional
    public void unenroll(UUID sessionPublicId, UUID enrollmentPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller);
        requireManualAudienceMutationAllowed(session);
        Enrollment enrollment = enrollmentRepository.findByPublicId(enrollmentPublicId)
                .orElseThrow(EnrollmentNotFoundException::new);
        if (!enrollment.getSession().getId().equals(session.getId())) {
            throw new EnrollmentNotFoundException();
        }
        UUID studentPublicId = enrollment.getStudentPublicId();
        if (!startedAttemptLookup.findStartedStudentPublicIds(session.getTenantId(), List.of(session.getPublicId()),
                List.of(studentPublicId)).isEmpty()) {
            throw new EnrolledStudentAlreadyStartedException();
        }
        formProvisioningService.release(session, studentPublicId);
        enrollmentRepository.delete(enrollment);
    }

    /** Backs enrollment's pending-exam-request transfer warning — one join-fetch query, no per-enrollment session lookup. */
    @Transactional(readOnly = true)
    public List<StudentEnrollmentResponse> listForStudent(UUID studentPublicId, CurrentUser caller) {
        return enrollmentRepository.findByStudentPublicIdAndTenantId(studentPublicId, caller.tenantId()).stream()
                .map(enrollment -> new StudentEnrollmentResponse(
                        enrollment.getPublicId(),
                        enrollment.getSession().getPublicId(),
                        enrollment.getSession().getName(),
                        enrollment.getSession().getStatus().name(),
                        enrollment.getSession().getOpensAt(),
                        enrollment.getSession().getClosesAt()))
                .toList();
    }

    @Transactional
    public ProctorAssignmentResponse assignProctor(UUID sessionPublicId, AssignProctorRequest request, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwned(sessionPublicId, caller);
        assertTenantActive(session);
        if (identityService != null) {
            identityService.requireActiveProctorInTenant(request.proctorPublicId(), session.getTenantId());
        }
        ProctorAssignment assignment = new ProctorAssignment();
        assignment.setSession(session);
        assignment.setProctorPublicId(request.proctorPublicId());
        assignment.setTenantId(session.getTenantId());
        assignment.setRole(request.role() != null ? request.role() : ProctorRole.ASSISTANT_PROCTOR);

        ProctorAssignment saved;
        try {
            saved = proctorAssignmentRepository.save(assignment);
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyAssignedException();
        }
        return toResponse(saved, session.getPublicId());
    }

    @Transactional(readOnly = true)
    public List<ProctorAssignmentResponse> listProctors(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwned(sessionPublicId, caller);
        return proctorAssignmentRepository.findBySessionId(session.getId()).stream()
                .map(assignment -> toResponse(assignment, session.getPublicId()))
                .toList();
    }

    @Transactional
    public ProctorAssignmentResponse updateProctorRole(UUID sessionPublicId, UUID assignmentPublicId,
                                                         UpdateProctorRoleRequest request, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwned(sessionPublicId, caller);
        ProctorAssignment assignment = proctorAssignmentRepository.findByPublicId(assignmentPublicId)
                .orElseThrow(ProctorAssignmentNotFoundException::new);
        if (!assignment.getSession().getId().equals(session.getId())) {
            throw new ProctorAssignmentNotFoundException();
        }
        assignment.setRole(request.role());
        ProctorAssignment saved = proctorAssignmentRepository.save(assignment);
        return toResponse(saved, session.getPublicId());
    }

    private ProctorAssignmentResponse toResponse(ProctorAssignment assignment, UUID sessionPublicId) {
        ProctorRole role = assignment.getRole() != null ? assignment.getRole() : ProctorRole.ASSISTANT_PROCTOR;
        return new ProctorAssignmentResponse(assignment.getPublicId(), sessionPublicId,
                assignment.getProctorPublicId(), role);
    }

    @Transactional
    public void unassignProctor(UUID sessionPublicId, UUID assignmentPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller);
        ProctorAssignment assignment = proctorAssignmentRepository.findByPublicId(assignmentPublicId)
                .orElseThrow(ProctorAssignmentNotFoundException::new);
        if (!assignment.getSession().getId().equals(session.getId())) {
            throw new ProctorAssignmentNotFoundException();
        }
        proctorAssignmentRepository.delete(assignment);
    }

    private Enrollment save(Enrollment enrollment) {
        try {
            return enrollmentRepository.save(enrollment);
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyEnrolledException();
        }
    }

    private List<SlotConflict> findConflicts(ExamSession session, List<UUID> studentPublicIds) {
        if (studentPublicIds.isEmpty()) {
            return List.of();
        }
        return enrollmentRepository.findConflictingEnrollments(session.getTenantId(), studentPublicIds,
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

    private void assertTenantActive(ExamSession session) {
        if (tenancyService != null) {
            tenancyService.assertTenantActive(session.getTenantId());
        }
    }

    private void requireActiveStudent(UUID studentPublicId, UUID tenantId) {
        if (identityService != null) {
            identityService.requireActiveStudentInTenant(studentPublicId, tenantId);
        }
    }

    private void requireActiveStudents(List<UUID> studentPublicIds, UUID tenantId) {
        if (identityService != null) {
            identityService.requireActiveStudentsInTenant(studentPublicIds, tenantId);
        }
    }

    private void lockActiveStudents(List<UUID> studentPublicIds, UUID tenantId) {
        if (identityService != null) {
            identityService.lockActiveStudentsInTenant(studentPublicIds, tenantId);
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
