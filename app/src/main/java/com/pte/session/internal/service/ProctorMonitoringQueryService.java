package com.pte.session.internal.service;

import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ProctorAssignment;
import com.pte.session.dto.response.MonitoringEnrollmentView;
import com.pte.session.dto.response.ProctorMonitoringSessionView;
import com.pte.session.internal.repository.EnrollmentRepository;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.session.internal.repository.ProctorAssignmentRepository;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Proctor-scoped read queries for assigned sessions, monitoring metadata, and enrollment rosters.
 */
@Service
public class ProctorMonitoringQueryService {

    private static final int DEFAULT_SIZE = 25;
    private static final int MAX_SIZE = 100;

    private final ProctorAssignmentRepository proctorAssignmentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ExamSessionRepository examSessionRepository;

    public ProctorMonitoringQueryService(
            ProctorAssignmentRepository proctorAssignmentRepository,
            EnrollmentRepository enrollmentRepository,
            ExamSessionRepository examSessionRepository) {
        this.proctorAssignmentRepository = proctorAssignmentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.examSessionRepository = examSessionRepository;
    }

    /**
     * Reads monitoring metadata for an exam session if the proctor is assigned to it within the caller's tenant.
     */
    @Transactional(readOnly = true)
    public Optional<ProctorMonitoringSessionView> getMonitoringSession(
            UUID sessionPublicId, UUID proctorPublicId, UUID tenantId) {
        if (sessionPublicId == null || proctorPublicId == null || tenantId == null) {
            return Optional.empty();
        }
        return proctorAssignmentRepository.findAssignment(sessionPublicId, proctorPublicId, tenantId)
                .map(this::toSessionView);
    }

    /**
     * Lists assigned sessions for a proctor in the caller's tenant, ordered by opensAt ASC nulls-last then publicId ASC.
     */
    @Transactional(readOnly = true)
    public PagedResult<ProctorMonitoringSessionView> getAssignedSessions(
            UUID proctorPublicId, UUID tenantId, int requestedPage, int requestedSize) {
        int page = Math.max(0, requestedPage);
        int size = requestedSize <= 0 ? DEFAULT_SIZE : Math.min(MAX_SIZE, requestedSize);
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("session.opensAt").nullsLast())
                        .and(Sort.by("session.publicId").ascending()));

        Page<ProctorAssignment> assignmentPage = proctorAssignmentRepository.findAssignedSessions(
                proctorPublicId, tenantId, pageable);

        List<ProctorMonitoringSessionView> items = assignmentPage.getContent().stream()
                .map(this::toSessionView)
                .toList();

        PageMeta meta = new PageMeta(
                assignmentPage.getNumber(),
                assignmentPage.getSize(),
                assignmentPage.getTotalElements(),
                assignmentPage.getTotalPages(),
                assignmentPage.isFirst(),
                assignmentPage.isLast(),
                assignmentPage.hasNext(),
                assignmentPage.hasPrevious());

        return new PagedResult<>(items, meta);
    }

    /**
     * Paged active student enrollments for an exam session, stable by enrollment publicId ASC.
     */
    @Transactional(readOnly = true)
    public PagedResult<MonitoringEnrollmentView> getEnrollments(
            UUID sessionPublicId, UUID tenantId, int requestedPage, int requestedSize) {
        int page = Math.max(0, requestedPage);
        int size = requestedSize <= 0 ? DEFAULT_SIZE : Math.min(MAX_SIZE, requestedSize);
        Pageable pageable = PageRequest.of(page, size, Sort.by("publicId").ascending());

        Page<com.pte.session.domain.Enrollment> enrollmentPage = enrollmentRepository
                .findBySessionPublicIdAndTenantIdAndDeletedFalse(sessionPublicId, tenantId, pageable);

        List<MonitoringEnrollmentView> items = enrollmentPage.getContent().stream()
                .map(e -> new MonitoringEnrollmentView(e.getPublicId(), e.getStudentPublicId()))
                .toList();

        PageMeta meta = new PageMeta(
                enrollPage(enrollmentPage),
                enrollmentPage.getSize(),
                enrollmentPage.getTotalElements(),
                enrollmentPage.getTotalPages(),
                enrollmentPage.isFirst(),
                enrollmentPage.isLast(),
                enrollmentPage.hasNext(),
                enrollmentPage.hasPrevious());

        return new PagedResult<>(items, meta);
    }

    private int enrollPage(Page<?> page) {
        return page.getNumber();
    }

    @Transactional(readOnly = true)
    public long countEnrollments(UUID sessionPublicId, UUID tenantId) {
        if (sessionPublicId == null || tenantId == null) {
            return 0L;
        }
        return enrollmentRepository.countBySessionPublicIdAndTenantIdAndDeletedFalse(sessionPublicId, tenantId);
    }

    @Transactional(readOnly = true)
    public boolean isStudentEnrolled(UUID sessionPublicId, UUID studentPublicId, UUID tenantId) {
        if (sessionPublicId == null || studentPublicId == null || tenantId == null) {
            return false;
        }
        return enrollmentRepository.isStudentEnrolled(sessionPublicId, studentPublicId, tenantId);
    }

    @Transactional(readOnly = true)
    public boolean isProctorAssigned(UUID sessionPublicId, UUID proctorPublicId, UUID tenantId) {
        if (sessionPublicId == null || proctorPublicId == null || tenantId == null) {
            return false;
        }
        return proctorAssignmentRepository.isProctorAssigned(sessionPublicId, proctorPublicId, tenantId);
    }

    private ProctorMonitoringSessionView toSessionView(ProctorAssignment pa) {
        ExamSession s = pa.getSession();
        String roleStr = pa.getRole() != null ? pa.getRole().name() : null;
        return new ProctorMonitoringSessionView(
                pa.getPublicId(),
                s.getPublicId(),
                s.getName(),
                s.getSessionCode(),
                s.getStatus(),
                s.getOpensAt(),
                s.getClosesAt(),
                roleStr,
                pa.getProctorPublicId());
    }
}
