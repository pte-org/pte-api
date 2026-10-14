package com.pte.session.internal.service;

import com.pte.session.internal.repository.ProctorAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Reads the assigned proctor recipients without exposing session repositories across modules. */
@Service
public class SessionStaffAssignmentQueryService {

    private final ProctorAssignmentRepository proctorAssignmentRepository;

    public SessionStaffAssignmentQueryService(ProctorAssignmentRepository proctorAssignmentRepository) {
        this.proctorAssignmentRepository = proctorAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public List<UUID> findAssignedProctorPublicIds(UUID sessionPublicId, UUID tenantId) {
        if (sessionPublicId == null || tenantId == null) {
            return List.of();
        }
        return proctorAssignmentRepository.findAssignedProctorPublicIds(sessionPublicId, tenantId)
                .stream().distinct().toList();
    }
}
