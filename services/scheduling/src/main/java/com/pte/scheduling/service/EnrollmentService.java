package com.pte.scheduling.service;

import com.pte.common.security.CurrentUser;
import com.pte.scheduling.constant.SchedulingConstants;
import com.pte.scheduling.domain.Enrollment;
import com.pte.scheduling.domain.ExamSession;
import com.pte.scheduling.domain.ProctorAssignment;
import com.pte.scheduling.domain.event.StudentEnrolledEvent;
import com.pte.scheduling.domain.exception.AlreadyAssignedException;
import com.pte.scheduling.domain.exception.AlreadyEnrolledException;
import com.pte.scheduling.dto.request.AssignProctorRequest;
import com.pte.scheduling.dto.request.EnrollStudentRequest;
import com.pte.scheduling.dto.response.EnrollmentResponse;
import com.pte.scheduling.dto.response.ProctorAssignmentResponse;
import com.pte.scheduling.messaging.outbox.OutboxWriter;
import com.pte.scheduling.repository.EnrollmentRepository;
import com.pte.scheduling.repository.ProctorAssignmentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public ProctorAssignmentResponse assignProctor(UUID sessionPublicId, AssignProctorRequest request, CurrentUser caller) {
        ExamSession session = sessionService.findOwned(sessionPublicId, caller);
        ProctorAssignment assignment = new ProctorAssignment();
        assignment.setSession(session);
        assignment.setProctorPublicId(request.proctorPublicId());
        assignment.setTenantId(session.getTenantId());

        ProctorAssignment saved;
        try {
            saved = proctorAssignmentRepository.save(assignment);
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyAssignedException();
        }
        return new ProctorAssignmentResponse(saved.getPublicId(), session.getPublicId(), saved.getProctorPublicId());
    }

    private Enrollment save(Enrollment enrollment) {
        try {
            return enrollmentRepository.save(enrollment);
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyEnrolledException();
        }
    }
}
