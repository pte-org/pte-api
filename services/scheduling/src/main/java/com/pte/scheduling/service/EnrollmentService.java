package com.pte.scheduling.service;

import com.pte.common.security.CurrentUser;
import com.pte.scheduling.constant.SchedulingConstants;
import com.pte.scheduling.domain.Enrollment;
import com.pte.scheduling.domain.ExamSession;
import com.pte.scheduling.domain.ProctorAssignment;
import com.pte.scheduling.domain.enums.ProctorRole;
import com.pte.scheduling.domain.event.ProctorAssignedEvent;
import com.pte.scheduling.domain.event.ProctorRoleUpdatedEvent;
import com.pte.scheduling.domain.event.ProctorUnassignedEvent;
import com.pte.scheduling.domain.event.StudentEnrolledEvent;
import com.pte.scheduling.domain.event.StudentUnenrolledEvent;
import com.pte.scheduling.domain.exception.AlreadyAssignedException;
import com.pte.scheduling.domain.exception.AlreadyEnrolledException;
import com.pte.scheduling.domain.exception.EnrollmentNotFoundException;
import com.pte.scheduling.domain.exception.ProctorAssignmentNotFoundException;
import com.pte.scheduling.dto.request.AssignProctorRequest;
import com.pte.scheduling.dto.request.BulkEnrollRequest;
import com.pte.scheduling.dto.request.EnrollStudentRequest;
import com.pte.scheduling.dto.request.UpdateProctorRoleRequest;
import com.pte.scheduling.dto.response.BulkEnrollResponse;
import com.pte.scheduling.dto.response.EnrollmentResponse;
import com.pte.scheduling.dto.response.ProctorAssignmentResponse;
import com.pte.scheduling.messaging.outbox.OutboxWriter;
import com.pte.scheduling.repository.EnrollmentRepository;
import com.pte.scheduling.repository.ProctorAssignmentRepository;
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
 * import). Double-enroll/assign is guarded by a DB unique constraint
 * (CODING_STANDARDS_API.md concurrency rule), not a check-then-act race.
 */
@Service
public class EnrollmentService {

    private final SessionService sessionService;
    private final EnrollmentRepository enrollmentRepository;
    private final ProctorAssignmentRepository proctorAssignmentRepository;
    private final OutboxWriter outboxWriter;

    public EnrollmentService(SessionService sessionService, EnrollmentRepository enrollmentRepository,
                             ProctorAssignmentRepository proctorAssignmentRepository, OutboxWriter outboxWriter) {
        this.sessionService = sessionService;
        this.enrollmentRepository = enrollmentRepository;
        this.proctorAssignmentRepository = proctorAssignmentRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public EnrollmentResponse enrollStudent(UUID sessionPublicId, EnrollStudentRequest request, CurrentUser caller) {
        ExamSession session = sessionService.findOwned(sessionPublicId, caller);
        Enrollment enrollment = new Enrollment();
        enrollment.setSession(session);
        enrollment.setStudentPublicId(request.studentPublicId());
        enrollment.setTenantId(session.getTenantId());

        Enrollment saved = save(enrollment);
        outboxWriter.write(SchedulingConstants.AGGREGATE_SESSION, session.getPublicId().toString(),
                SchedulingConstants.EVENT_STUDENT_ENROLLED,
                new StudentEnrolledEvent(session.getPublicId(), request.studentPublicId(), session.getTenantId()),
                session.getTenantId());
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
     */
    @Transactional
    public BulkEnrollResponse bulkEnroll(UUID sessionPublicId, BulkEnrollRequest request, CurrentUser caller) {
        ExamSession session = sessionService.findOwned(sessionPublicId, caller);

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

        List<Enrollment> saved;
        try {
            saved = enrollmentRepository.saveAll(toCreate);
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyEnrolledException();
        }

        for (Enrollment enrollment : saved) {
            outboxWriter.write(SchedulingConstants.AGGREGATE_SESSION, session.getPublicId().toString(),
                    SchedulingConstants.EVENT_STUDENT_ENROLLED,
                    new StudentEnrolledEvent(session.getPublicId(), enrollment.getStudentPublicId(), session.getTenantId()),
                    session.getTenantId());
        }

        List<UUID> enrolled = saved.stream().map(Enrollment::getStudentPublicId).toList();
        return new BulkEnrollResponse(enrolled, List.copyOf(alreadyEnrolled));
    }

    @Transactional(readOnly = true)
    public List<EnrollmentResponse> list(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionService.findOwned(sessionPublicId, caller);
        return enrollmentRepository.findBySessionId(session.getId()).stream()
                .map(enrollment -> new EnrollmentResponse(enrollment.getPublicId(), session.getPublicId(),
                        enrollment.getStudentPublicId()))
                .toList();
    }

    @Transactional
    public void unenroll(UUID sessionPublicId, UUID enrollmentPublicId, CurrentUser caller) {
        ExamSession session = sessionService.findOwned(sessionPublicId, caller);
        Enrollment enrollment = enrollmentRepository.findByPublicId(enrollmentPublicId)
                .orElseThrow(EnrollmentNotFoundException::new);
        if (!enrollment.getSession().getId().equals(session.getId())) {
            throw new EnrollmentNotFoundException();
        }
        enrollmentRepository.delete(enrollment);
        outboxWriter.write(SchedulingConstants.AGGREGATE_SESSION, session.getPublicId().toString(),
                SchedulingConstants.EVENT_STUDENT_UNENROLLED,
                new StudentUnenrolledEvent(session.getPublicId(), enrollment.getStudentPublicId(), session.getTenantId()),
                session.getTenantId());
    }

    @Transactional
    public ProctorAssignmentResponse assignProctor(UUID sessionPublicId, AssignProctorRequest request, CurrentUser caller) {
        ExamSession session = sessionService.findOwned(sessionPublicId, caller);
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
        outboxWriter.write(SchedulingConstants.AGGREGATE_SESSION, session.getPublicId().toString(),
                SchedulingConstants.EVENT_PROCTOR_ASSIGNED,
                new ProctorAssignedEvent(session.getPublicId(), saved.getProctorPublicId(), session.getTenantId()),
                session.getTenantId());
        return toResponse(saved, session.getPublicId());
    }

    @Transactional(readOnly = true)
    public List<ProctorAssignmentResponse> listProctors(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionService.findOwned(sessionPublicId, caller);
        return proctorAssignmentRepository.findBySessionId(session.getId()).stream()
                .map(assignment -> toResponse(assignment, session.getPublicId()))
                .toList();
    }

    @Transactional
    public ProctorAssignmentResponse updateProctorRole(UUID sessionPublicId, UUID assignmentPublicId,
                                                         UpdateProctorRoleRequest request, CurrentUser caller) {
        ExamSession session = sessionService.findOwned(sessionPublicId, caller);
        ProctorAssignment assignment = proctorAssignmentRepository.findByPublicId(assignmentPublicId)
                .orElseThrow(ProctorAssignmentNotFoundException::new);
        if (!assignment.getSession().getId().equals(session.getId())) {
            throw new ProctorAssignmentNotFoundException();
        }
        assignment.setRole(request.role());
        ProctorAssignment saved = proctorAssignmentRepository.save(assignment);
        outboxWriter.write(SchedulingConstants.AGGREGATE_SESSION, session.getPublicId().toString(),
                SchedulingConstants.EVENT_PROCTOR_ROLE_UPDATED,
                new ProctorRoleUpdatedEvent(session.getPublicId(), saved.getProctorPublicId(), saved.getRole(),
                        session.getTenantId()),
                session.getTenantId());
        return toResponse(saved, session.getPublicId());
    }

    private ProctorAssignmentResponse toResponse(ProctorAssignment assignment, UUID sessionPublicId) {
        ProctorRole role = assignment.getRole() != null ? assignment.getRole() : ProctorRole.ASSISTANT_PROCTOR;
        return new ProctorAssignmentResponse(assignment.getPublicId(), sessionPublicId,
                assignment.getProctorPublicId(), role);
    }

    @Transactional
    public void unassignProctor(UUID sessionPublicId, UUID assignmentPublicId, CurrentUser caller) {
        ExamSession session = sessionService.findOwned(sessionPublicId, caller);
        ProctorAssignment assignment = proctorAssignmentRepository.findByPublicId(assignmentPublicId)
                .orElseThrow(ProctorAssignmentNotFoundException::new);
        if (!assignment.getSession().getId().equals(session.getId())) {
            throw new ProctorAssignmentNotFoundException();
        }
        proctorAssignmentRepository.delete(assignment);
        outboxWriter.write(SchedulingConstants.AGGREGATE_SESSION, session.getPublicId().toString(),
                SchedulingConstants.EVENT_PROCTOR_UNASSIGNED,
                new ProctorUnassignedEvent(session.getPublicId(), assignment.getProctorPublicId(), session.getTenantId()),
                session.getTenantId());
    }

    private Enrollment save(Enrollment enrollment) {
        try {
            return enrollmentRepository.save(enrollment);
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyEnrolledException();
        }
    }
}
