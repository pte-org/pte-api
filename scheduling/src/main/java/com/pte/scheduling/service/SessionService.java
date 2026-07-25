package com.pte.scheduling.service;

import com.pte.common.security.CurrentUser;
import com.pte.scheduling.constant.SchedulingConstants;
import com.pte.scheduling.domain.ExamSession;
import com.pte.scheduling.domain.SnapshotRef;
import com.pte.scheduling.domain.event.SessionScheduledEvent;
import com.pte.scheduling.domain.exception.HostContextRequiredException;
import com.pte.scheduling.domain.exception.InvalidSessionWindowException;
import com.pte.scheduling.domain.exception.SessionNotFoundException;
import com.pte.scheduling.dto.request.CreateSessionRequest;
import com.pte.scheduling.dto.response.SessionResponse;
import com.pte.scheduling.mapper.SessionMapper;
import com.pte.scheduling.messaging.outbox.OutboxWriter;
import com.pte.scheduling.repository.ExamSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Session lifecycle: create (resolving the snapshot ref via {@link SnapshotRefService}),
 * open, close. Tenant-scoped throughout — a host operates only on its own tenant's
 * sessions.
 */
@Service
public class SessionService {

    private final ExamSessionRepository sessionRepository;
    private final SnapshotRefService snapshotRefService;
    private final OutboxWriter outboxWriter;

    public SessionService(ExamSessionRepository sessionRepository, SnapshotRefService snapshotRefService,
                          OutboxWriter outboxWriter) {
        this.sessionRepository = sessionRepository;
        this.snapshotRefService = snapshotRefService;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public SessionResponse create(CreateSessionRequest request, CurrentUser caller) {
        UUID tenantId = requireTenant(caller);
        if (!request.closesAt().isAfter(request.opensAt())) {
            throw new InvalidSessionWindowException();
        }
        SnapshotRef snapshotRef = snapshotRefService.resolve(request.snapshotPublicId());

        ExamSession session = new ExamSession();
        session.setName(request.name());
        session.setTenantId(tenantId);
        session.setSnapshotPublicId(snapshotRef.getSnapshotPublicId());
        session.setOpensAt(request.opensAt());
        session.setClosesAt(request.closesAt());
        ExamSession saved = sessionRepository.save(session);

        outboxWriter.write(SchedulingConstants.AGGREGATE_SESSION, saved.getPublicId().toString(),
                SchedulingConstants.EVENT_SESSION_SCHEDULED,
                new SessionScheduledEvent(saved.getPublicId(), saved.getSnapshotPublicId(), tenantId,
                        saved.getOpensAt(), saved.getClosesAt()),
                tenantId);
        return SessionMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public SessionResponse get(UUID publicId, CurrentUser caller) {
        return SessionMapper.toResponse(findOwned(publicId, caller));
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list(CurrentUser caller) {
        return sessionRepository.findByTenantId(requireTenant(caller)).stream()
                .map(SessionMapper::toResponse).toList();
    }

    @Transactional
    public SessionResponse open(UUID publicId, CurrentUser caller) {
        ExamSession session = findOwned(publicId, caller);
        session.open();
        return SessionMapper.toResponse(session);
    }

    @Transactional
    public SessionResponse close(UUID publicId, CurrentUser caller) {
        ExamSession session = findOwned(publicId, caller);
        session.close();
        return SessionMapper.toResponse(session);
    }

    ExamSession findOwned(UUID publicId, CurrentUser caller) {
        return sessionRepository.findWithCompositionByPublicIdAndTenantId(publicId, requireTenant(caller))
                .orElseThrow(SessionNotFoundException::new);
    }

    private UUID requireTenant(CurrentUser caller) {
        if (caller.tenantId() == null) {
            throw new HostContextRequiredException();
        }
        return caller.tenantId();
    }
}
