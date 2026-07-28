package com.pte.scheduling.service;

import com.pte.common.security.CurrentUser;
import com.pte.scheduling.domain.ProctorAssignment;
import com.pte.scheduling.domain.exception.ProctorContextRequiredException;
import com.pte.scheduling.dto.response.AssignedProctorSessionResponse;
import com.pte.scheduling.repository.ProctorAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProctorAssignmentQueryService {

    private static final String ROLE_PROCTOR = "PROCTOR";

    private final ProctorAssignmentRepository repository;

    public ProctorAssignmentQueryService(ProctorAssignmentRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AssignedProctorSessionResponse> listMine(CurrentUser caller) {
        if (caller.tenantId() == null || !caller.hasRole(ROLE_PROCTOR)) {
            throw new ProctorContextRequiredException();
        }
        return repository.findAssignedSessions(caller.userId(), caller.tenantId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AssignedProctorSessionResponse toResponse(ProctorAssignment assignment) {
        var session = assignment.getSession();
        return new AssignedProctorSessionResponse(
                assignment.getPublicId(),
                session.getPublicId(),
                session.getName(),
                session.getOpensAt(),
                session.getClosesAt(),
                session.getStatus().name());
    }
}
