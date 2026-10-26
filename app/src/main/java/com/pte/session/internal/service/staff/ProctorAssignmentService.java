package com.pte.session.internal.service.staff;

import com.pte.session.internal.service.SessionLifecycleService;
import com.pte.identity.IdentityService;
import com.pte.session.domain.ExamSession;
import com.pte.session.domain.ExamSessionProctor;
import com.pte.session.domain.enums.ProctorRole;
import com.pte.session.internal.dto.request.AssignProctorRequest;
import com.pte.session.internal.dto.request.UpdateProctorRoleRequest;
import com.pte.session.internal.dto.response.proctor.ProctorAssignmentResponse;
import com.pte.session.internal.exception.staff.AlreadyAssignedException;
import com.pte.session.internal.exception.staff.ProctorAssignmentNotFoundException;
import com.pte.session.internal.repository.ExamSessionProctorRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
        ExamSessionProctor assignment = new ExamSessionProctor();
        assignment.setSession(session);
        assignment.setProctorPublicId(request.proctorPublicId());
        assignment.setTenantId(session.getTenantId());
        assignment.setRole(request.role() != null ? request.role() : ProctorRole.ASSISTANT_PROCTOR);
        try {
            return toResponse(proctorRepository.save(assignment), session.getPublicId());
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyAssignedException();
        }
    }

    @Transactional(readOnly = true)
    public List<ProctorAssignmentResponse> listProctors(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwned(sessionPublicId, caller);
        return proctorRepository.findBySessionId(session.getId()).stream()
                .map(assignment -> toResponse(assignment, session.getPublicId()))
                .toList();
    }

    @Transactional
    public ProctorAssignmentResponse updateProctorRole(UUID sessionPublicId, UUID assignmentPublicId,
            UpdateProctorRoleRequest request, CurrentUser caller) {
        ExamSession session = sessionLifecycleService.findOwned(sessionPublicId, caller);
        ExamSessionProctor assignment = ownedAssignment(session, assignmentPublicId);
        assignment.setRole(request.role());
        return toResponse(proctorRepository.save(assignment), session.getPublicId());
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
        ProctorRole role = assignment.getRole() != null ? assignment.getRole() : ProctorRole.ASSISTANT_PROCTOR;
        return new ProctorAssignmentResponse(assignment.getPublicId(), sessionPublicId,
                assignment.getProctorPublicId(), role);
    }
}
