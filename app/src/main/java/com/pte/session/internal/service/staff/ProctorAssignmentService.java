package com.pte.session.internal.service.staff;

import com.pte.session.internal.service.SessionLifecycleService;
import com.pte.identity.IdentityService;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ExamSessionProctor;
import com.pte.session.internal.dto.request.AssignProctorRequest;
import com.pte.session.internal.dto.response.proctor.ProctorAssignmentResponse;
import com.pte.session.internal.exception.staff.AlreadyAssignedException;
import com.pte.session.internal.exception.staff.ProctorAssignmentNotFoundException;
import com.pte.session.internal.repository.ExamSessionProctorRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Assigns proctors to a session; a double assignment is rejected by the DB unique constraint. */
@Service
public class ProctorAssignmentService {

    private final SessionLifecycleService sessionLifecycleService;
    private final ExamSessionProctorRepository proctorRepository;
    private final TenancyService tenancyService;
    private final IdentityService identityService;

    public ProctorAssignmentService(SessionLifecycleService sessionLifecycleService,
            ExamSessionProctorRepository proctorRepository, TenancyService tenancyService,
            IdentityService identityService) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.proctorRepository = proctorRepository;
        this.tenancyService = tenancyService;
        this.identityService = identityService;
    }

    @Transactional
    public ProctorAssignmentResponse assignProctor(UUID sessionPublicId, AssignProctorRequest request,
            CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwned(sessionPublicId, caller);
        tenancyService.assertTenantActive(session.getTenantId());
        identityService.requireActiveProctorInTenant(request.proctorPublicId(), session.getTenantId());
        ExamSessionProctor assignment = newAssignment(session, request);
        try {
            return toResponse(proctorRepository.save(assignment), session.getPublicId());
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyAssignedException();
        }
    }

    /**
     * Assigns the proctors picked while an exam is being created. It joins the caller's transaction, so one proctor
     * that is not eligible, or one picked twice, rejects the whole creation and leaves no draft behind.
     */
    @Transactional
    public void assignAtCreation(ExamSession session, List<AssignProctorRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        Set<UUID> picked = new HashSet<>();
        List<ExamSessionProctor> assignments = new ArrayList<>();
        for (AssignProctorRequest request : requests) {
            if (!picked.add(request.proctorPublicId())) {
                throw new AlreadyAssignedException();
            }
            identityService.requireActiveProctorInTenant(request.proctorPublicId(), session.getTenantId());
            assignments.add(newAssignment(session, request));
        }
        proctorRepository.saveAll(assignments);
    }

    private ExamSessionProctor newAssignment(ExamSession session, AssignProctorRequest request) {
        ExamSessionProctor assignment = new ExamSessionProctor();
        assignment.setSession(session);
        assignment.setProctorPublicId(request.proctorPublicId());
        assignment.setTenantId(session.getTenantId());
        return assignment;
    }

    @Transactional(readOnly = true)
    public List<ProctorAssignmentResponse> listProctors(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwned(sessionPublicId, caller);
        return proctorRepository.findBySessionId(session.getId()).stream()
                .map(assignment -> toResponse(assignment, session.getPublicId()))
                .toList();
    }

    @Transactional
    public void unassignProctor(UUID sessionPublicId, UUID assignmentPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwnedWithLock(sessionPublicId, caller);
        proctorRepository.delete(ownedAssignment(session, assignmentPublicId));
    }

    private ExamSessionProctor ownedAssignment(ExamSession session, UUID assignmentPublicId) {
        ExamSessionProctor assignment = proctorRepository.findByPublicId(assignmentPublicId)
                .orElseThrow(ProctorAssignmentNotFoundException::new);
        if (!assignment.getSession().getId().equals(session.getId())) {
            throw new ProctorAssignmentNotFoundException();
        }
        return assignment;
    }

    private ProctorAssignmentResponse toResponse(ExamSessionProctor assignment, UUID sessionPublicId) {
        return new ProctorAssignmentResponse(assignment.getPublicId(), sessionPublicId,
                assignment.getProctorPublicId());
    }
}
