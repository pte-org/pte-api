package com.pte.session.internal.service.staff;

import com.pte.session.internal.repository.ExamSessionProctorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Reads the assigned proctor recipients without exposing session repositories across modules. */
@Service
public class SessionStaffAssignmentQueryService {

    private final ExamSessionProctorRepository proctorRepository;

    public SessionStaffAssignmentQueryService(ExamSessionProctorRepository proctorRepository) {
        this.proctorRepository = proctorRepository;
    }

    @Transactional(readOnly = true)
    public List<UUID> findAssignedProctorPublicIds(UUID sessionPublicId, UUID tenantId) {
        if (sessionPublicId == null || tenantId == null) {
            return List.of();
        }
        return proctorRepository.findAssignedProctorPublicIds(sessionPublicId, tenantId)
                .stream().distinct().toList();
    }
}
