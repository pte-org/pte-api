package com.pte.proctoring.internal.service;

import com.pte.proctoring.domain.ProctorSession;
import com.pte.proctoring.domain.ViolationEvent;
import com.pte.proctoring.dto.event.ViolationDetectedEvent;
import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.proctoring.internal.exception.ProctorSessionNotActiveException;
import com.pte.proctoring.internal.dto.request.FlagViolationRequest;
import com.pte.proctoring.internal.dto.response.ViolationEventResponse;
import com.pte.proctoring.internal.mapper.ProctorMapper;
import com.pte.proctoring.internal.repository.ProctorSessionRepository;
import com.pte.proctoring.internal.repository.ViolationEventRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;
    private final ProctorMapper mapper;
    private final SimpMessagingTemplate messagingTemplate;

    public ViolationService(ProctorSessionService proctorSessionService, ProctorSessionRepository proctorSessionRepository,
                            ViolationEventRepository violationEventRepository, HashChainService hashChainService,
                            ApplicationEventPublisher eventPublisher, ProctorMapper mapper,
                            SimpMessagingTemplate messagingTemplate) {
        this.proctorSessionService = proctorSessionService;
        this.proctorSessionRepository = proctorSessionRepository;
        this.violationEventRepository = violationEventRepository;
        this.hashChainService = hashChainService;
        this.eventPublisher = eventPublisher;
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

        // Fires only after this transaction commits (notification's listener is
        // @TransactionalEventListener) — a rolled-back flag never notifies anyone.
        eventPublisher.publishEvent(new ViolationDetectedEvent(request.attemptPublicId(), session.getSessionPublicId(),
                request.violationType(), request.detail(), detectedAt, session.getTenantId()));

        ViolationEventResponse response = mapper.toResponse(saved);
        messagingTemplate.convertAndSend(ProctorConstants.TOPIC_PREFIX + session.getSessionPublicId(), response);
        return response;
    }

    @Transactional(readOnly = true)
    public List<ViolationEventResponse> listForSession(UUID sessionPublicId, CurrentUser caller) {
        return violationEventRepository.findBySessionPublicIdAndTenantIdOrderByDetectedAtAsc(sessionPublicId, caller.tenantId())
                .stream().map(mapper::toResponse).toList();
    }
}
