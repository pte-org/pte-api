package com.pte.session.internal.service;

import com.pte.session.domain.Enrollment;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ProctorAssignment;
import com.pte.session.domain.enums.ProctorRole;
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
import com.pte.session.internal.exception.EnrollmentNotFoundException;
import com.pte.session.internal.exception.ProctorAssignmentNotFoundException;
import com.pte.session.internal.exception.SessionCapacityExceededException;
import com.pte.session.internal.repository.EnrollmentRepository;
import com.pte.session.internal.repository.ProctorAssignmentRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
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

    public EnrollmentService(SessionLifecycleService sessionLifecycleService, EnrollmentRepository enrollmentRepository,
                             ProctorAssignmentRepository proctorAssignmentRepository) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.enrollmentRepository = enrollmentRepository;
        this.proctorAssignmentRepository = proctorAssignmentRepository;
    }

    @Transactional
    public EnrollmentResponse enrollStudent(UUID sessionPublicId, EnrollStudentRequest request, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwned(sessionPublicId, caller);
        Enrollment enrollment = new Enrollment();
        enrollment.setSession(session);
        enrollment.setStudentPublicId(request.studentPublicId());
        enrollment.setTenantId(session.getTenantId());

        Enrollment saved = save(enrollment);
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

        List<UUID> existing = enrollmentRepository
                .findBySessionIdAndStudentPublicIdIn(session.getId(), request.studentPublicIds())
                .stream().map(Enrollment::getStudentPublicId).toList();
        Set<UUID> alreadyEnrolled = new HashSet<>(existing);

        List<Enrollment> toCreate = new ArrayList<>();
        Set<UUID> seenInBatch = new HashSet<>();
        for (UUID studentPublicId : request.studentPublicIds()) {
            if (alreadyEnrolled.contains(studentPublicId) || !seenInBatch.add(studentPublicId)) {
                continue;
            }
            Enrollment enrollment = new Enrollment();
            enrollment.setSession(session);
            enrollment.setStudentPublicId(studentPublicId);
            enrollment.setTenantId(session.getTenantId());
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

        List<UUID> enrolled = saved.stream().map(Enrollment::getStudentPublicId).toList();
        return new BulkEnrollResponse(enrolled, List.copyOf(alreadyEnrolled));
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
        ExamSession session = sessionLifecycleService.findOwned(sessionPublicId, caller);
        Enrollment enrollment = enrollmentRepository.findByPublicId(enrollmentPublicId)
                .orElseThrow(EnrollmentNotFoundException::new);
        if (!enrollment.getSession().getId().equals(session.getId())) {
            throw new EnrollmentNotFoundException();
        }
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
        ExamSession session = sessionLifecycleService.findOwned(sessionPublicId, caller);
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
}
