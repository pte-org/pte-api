package com.pte.scheduling.service;

import com.pte.common.security.CurrentUser;
import com.pte.scheduling.constant.SchedulingConstants;
import com.pte.scheduling.domain.ExamSession;
import com.pte.scheduling.domain.event.PublishRequestedEvent;
import com.pte.scheduling.domain.event.ScoringRequestedEvent;
import com.pte.scheduling.messaging.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Origin of the host-gated scoring/publish commands (ADR-002). scheduling
 * DECIDES when to trigger these — scoring/reporting only EXECUTE on command,
 * they never self-trigger on submission. This is the host-facing side of the
 * host-gated scoring model.
 */
@Service
public class HostCommandService {

    private final SessionService sessionService;
    private final OutboxWriter outboxWriter;

    public HostCommandService(SessionService sessionService, OutboxWriter outboxWriter) {
        this.sessionService = sessionService;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public void requestScoring(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionService.findOwned(sessionPublicId, caller);
        outboxWriter.write(SchedulingConstants.AGGREGATE_SESSION, session.getPublicId().toString(),
                SchedulingConstants.EVENT_SCORING_REQUESTED,
                new ScoringRequestedEvent(session.getPublicId(), session.getTenantId()),
                session.getTenantId());
    }

    @Transactional
    public void requestPublish(UUID sessionPublicId, CurrentUser caller) {
        ExamSession session = sessionService.findOwned(sessionPublicId, caller);
        outboxWriter.write(SchedulingConstants.AGGREGATE_SESSION, session.getPublicId().toString(),
                SchedulingConstants.EVENT_PUBLISH_REQUESTED,
                new PublishRequestedEvent(session.getPublicId(), session.getTenantId()),
                session.getTenantId());
    }
}
