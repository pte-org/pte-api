package com.pte.proctoring.internal.service;

import com.pte.proctoring.domain.enums.IncidentOrigin;
import com.pte.proctoring.internal.dto.response.ViolationEventResponse;
import com.pte.proctoring.internal.mapper.ProctorMapper;
import com.pte.proctoring.internal.policy.ProctorAccessPolicy;
import com.pte.proctoring.internal.repository.ExamIncidentRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Read side of proctor-flagged violations. Proctors no longer flag violations live; the list keeps the
 * PROCTOR_FLAG incidents recorded before that, so existing session audits stay readable.
 */
@Service
public class ViolationService {

    private final ExamIncidentRepository incidentRepository;
    private final ProctorMapper mapper;
    private final ProctorAccessPolicy proctorAccessPolicy;

    public ViolationService(ExamIncidentRepository incidentRepository, ProctorMapper mapper,
                            ProctorAccessPolicy proctorAccessPolicy) {
        this.incidentRepository = incidentRepository;
        this.mapper = mapper;
        this.proctorAccessPolicy = proctorAccessPolicy;
    }

    @Transactional(readOnly = true)
    public List<ViolationEventResponse> listForSession(UUID sessionPublicId, CurrentUser caller) {
        UUID tenantId = proctorAccessPolicy.authorizeReadAssigned(sessionPublicId, caller);
        return incidentRepository.findBySessionPublicIdAndTenantIdAndOriginAndDeletedFalseOrderByDetectedAtAsc(
                        sessionPublicId, tenantId, IncidentOrigin.PROCTOR_FLAG)
                .stream().map(mapper::toResponse).toList();
    }
}
