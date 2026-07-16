package com.aptis.modules.proctor.service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.modules.proctor.domain.ProctorAction;
import com.aptis.modules.proctor.domain.ProctorActionType;
import com.aptis.modules.proctor.repository.ProctorActionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Writes the immutable {@link ProctorAction} audit row. Callers (examdelivery's proctor
 * action service, and this module's own broadcast service) are responsible for including
 * this call in the SAME transaction as the state mutation it's auditing — this service does
 * not open its own transaction boundary that would let the two commit independently.
 */
@Service
public class ProctorActionAuditService {

    private final ProctorActionRepository proctorActionRepository;
    private final ObjectMapper objectMapper;

    public ProctorActionAuditService(ProctorActionRepository proctorActionRepository, ObjectMapper objectMapper) {
        this.proctorActionRepository = proctorActionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void record(Long actorProctorId, Long targetAttemptId, ProctorActionType type, Map<String, Object> details) {
        String detailsJson = toJson(details);
        ProctorAction action = ProctorAction.create(actorProctorId, targetAttemptId, type, detailsJson);
        proctorActionRepository.save(action);
    }

    private String toJson(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception exception) {
            // Audit detail serialization must never silently drop the record's existence —
            // fall back to toString() so the row is still written with best-effort context.
            return String.valueOf(details);
        }
    }
}
