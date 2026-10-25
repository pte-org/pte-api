package com.pte.proctoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.proctoring.domain.ExamIncident;
import com.pte.proctoring.domain.enums.IncidentOrigin;
import com.pte.proctoring.dto.event.ViolationDetectedEvent;
import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.proctoring.internal.dto.request.FlagViolationRequest;
import com.pte.proctoring.internal.dto.response.ViolationEventResponse;
import com.pte.proctoring.internal.exception.TargetAttemptInvalidException;
import com.pte.proctoring.internal.mapper.ProctorMapper;
import com.pte.proctoring.internal.policy.ProctorAccessPolicy;
import com.pte.proctoring.internal.repository.ExamIncidentRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A proctor flagging a violation on a live attempt: recorded as a PROCTOR_FLAG incident and broadcast to the room. */
@Service
public class ViolationService {

    private final ProctorWatchService watchService;
    private final ExamIncidentRepository incidentRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ProctorMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final AttemptService attemptService;
    private final ProctorAccessPolicy proctorAccessPolicy;

    public ViolationService(ProctorWatchService watchService, ExamIncidentRepository incidentRepository,
                            ApplicationEventPublisher eventPublisher, ProctorMapper mapper,
                            SimpMessagingTemplate messagingTemplate, AttemptService attemptService,
                            ProctorAccessPolicy proctorAccessPolicy) {
        this.watchService = watchService;
        this.incidentRepository = incidentRepository;
        this.eventPublisher = eventPublisher;
        this.mapper = mapper;
        this.messagingTemplate = messagingTemplate;
        this.attemptService = attemptService;
        this.proctorAccessPolicy = proctorAccessPolicy;
    }

    @Transactional
    public ViolationEventResponse flag(UUID sessionPublicId, FlagViolationRequest request, CurrentUser caller) {
        UUID tenantId = watchService.authorizeCommand(sessionPublicId, caller);
        if (!attemptService.attemptBelongsToSession(request.attemptPublicId(), sessionPublicId, tenantId)) {
            throw new TargetAttemptInvalidException();
        }
        Instant detectedAt = Instant.now();
        ExamIncident incident = new ExamIncident();
        incident.setTenantId(tenantId);
        incident.setSessionPublicId(sessionPublicId);
        incident.setAttemptPublicId(request.attemptPublicId());
        incident.setOrigin(IncidentOrigin.PROCTOR_FLAG);
        incident.setType(request.violationType().name());
        incident.setTitle(request.violationType().name());
        incident.setDescription(request.detail());
        incident.setSeverity(ProctorConstants.PROCTOR_FLAG_SEVERITY);
        incident.setReporterRole(ProctorConstants.PROCTOR_ROLE);
        incident.setReporterPublicId(caller.userId());
        incident.setDetectedAt(detectedAt);
        ViolationEventResponse response = mapper.toResponse(incidentRepository.save(incident));

        // Fires only after this transaction commits (the notification listener is transactional), so a
        // rolled-back flag never notifies anyone.
        eventPublisher.publishEvent(new ViolationDetectedEvent(request.attemptPublicId(), sessionPublicId,
                request.violationType(), request.detail(), detectedAt, tenantId));
        broadcastAfterCommit(sessionPublicId, response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ViolationEventResponse> listForSession(UUID sessionPublicId, CurrentUser caller) {
        UUID tenantId = proctorAccessPolicy.authorizeReadAssigned(sessionPublicId, caller);
        return incidentRepository.findBySessionPublicIdAndTenantIdAndOriginAndDeletedFalseOrderByDetectedAtAsc(
                        sessionPublicId, tenantId, IncidentOrigin.PROCTOR_FLAG)
                .stream().map(mapper::toResponse).toList();
    }

    private void broadcastAfterCommit(UUID sessionPublicId, ViolationEventResponse response) {
        String topic = ProctorConstants.TOPIC_PREFIX + sessionPublicId;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    messagingTemplate.convertAndSend(topic, response);
                }
            });
        } else {
            messagingTemplate.convertAndSend(topic, response);
        }
    }
}
