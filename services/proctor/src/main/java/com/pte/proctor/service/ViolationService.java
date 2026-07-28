package com.pte.proctor.service;

import com.pte.common.security.CurrentUser;
import com.pte.proctor.constant.ProctorConstants;
import com.pte.proctor.domain.ProctorSession;
import com.pte.proctor.domain.ViolationEvent;
import com.pte.proctor.domain.enums.LiveProctorEventType;
import com.pte.proctor.domain.event.ViolationDetectedEvent;
import com.pte.proctor.domain.exception.ProctorSessionNotActiveException;
import com.pte.proctor.dto.request.FlagViolationRequest;
import com.pte.proctor.dto.response.LiveProctorEventResponse;
import com.pte.proctor.dto.response.ViolationEventResponse;
import com.pte.proctor.mapper.ProctorMapper;
import com.pte.proctor.messaging.outbox.OutboxWriter;
import com.pte.proctor.repository.ProctorSessionRepository;
import com.pte.proctor.repository.ViolationEventRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Appends one link to a {@link ProctorSession}'s tamper-evident violation
 * chain. The new hash and the session's {@code lastSequenceNo}/{@code
 * lastHash} head advance together in one transaction — no read-then-write
 * race on "what's the current head."
 */
@Service
public class ViolationService {

    private final ProctorSessionService proctorSessionService;
    private final ProctorSessionRepository proctorSessionRepository;
    private final ViolationEventRepository violationEventRepository;
    private final HashChainService hashChainService;
    private final OutboxWriter outboxWriter;
    private final ProctorMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;

    public ViolationService(ProctorSessionService proctorSessionService, ProctorSessionRepository proctorSessionRepository,
                            ViolationEventRepository violationEventRepository, HashChainService hashChainService,
                            OutboxWriter outboxWriter, ProctorMapper mapper, SimpMessagingTemplate messagingTemplate) {
        this.proctorSessionService = proctorSessionService;
        this.proctorSessionRepository = proctorSessionRepository;
        this.violationEventRepository = violationEventRepository;
        this.hashChainService = hashChainService;
        this.outboxWriter = outboxWriter;
        this.mapper = mapper;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
    public ViolationEventResponse flag(UUID proctorSessionPublicId, FlagViolationRequest request, CurrentUser caller) {
        ProctorSession session = proctorSessionService.findOwned(proctorSessionPublicId, caller.userId(), caller.tenantId());
        if (!session.isActive()) {
            throw new ProctorSessionNotActiveException();
        }

        int nextSequenceNo = session.getLastSequenceNo() + 1;
        Instant detectedAt = Instant.now();
        String hash = hashChainService.computeHash(session.getSessionPublicId(), nextSequenceNo,
                request.attemptPublicId(), request.violationType(), request.detail(), detectedAt, session.getLastHash());

        ViolationEvent event = new ViolationEvent();
        event.setProctorSession(session);
        event.setSessionPublicId(session.getSessionPublicId());
        event.setAttemptPublicId(request.attemptPublicId());
        event.setTenantId(session.getTenantId());
        event.setViolationType(request.violationType());
        event.setDetail(request.detail());
        event.setSequenceNo(nextSequenceNo);
        event.setPrevHash(session.getLastHash());
        event.setHash(hash);
        event.setDetectedAt(detectedAt);
        ViolationEvent saved = violationEventRepository.save(event);

        session.advanceChain(hash);
        proctorSessionRepository.save(session);

        outboxWriter.write(ProctorConstants.AGGREGATE_VIOLATION_EVENT, saved.getPublicId().toString(),
                ProctorConstants.EVENT_VIOLATION_DETECTED,
                new ViolationDetectedEvent(request.attemptPublicId(), session.getSessionPublicId(),
                        request.violationType(), request.detail(), detectedAt, session.getTenantId()),
                session.getTenantId());

        ViolationEventResponse response = mapper.toResponse(saved);
        messagingTemplate.convertAndSend(
                ProctorConstants.TOPIC_PREFIX + session.getSessionPublicId(),
                new LiveProctorEventResponse<>(
                        response.publicId(),
                        LiveProctorEventType.VIOLATION_DETECTED,
                        session.getSessionPublicId(),
                        response.detectedAt(),
                        response));
        return response;
    }

    @Transactional(readOnly = true)
    public List<ViolationEventResponse> listForSession(UUID sessionPublicId, CurrentUser caller) {
        if (caller.hasRole("PROCTOR")) {
            proctorSessionService.findActiveOwnedBySession(
                    sessionPublicId,
                    caller.userId(),
                    caller.tenantId());
        }
        return violationEventRepository.findBySessionPublicIdAndTenantIdOrderByDetectedAtAsc(sessionPublicId, caller.tenantId())
                .stream().map(mapper::toResponse).toList();
    }
}
