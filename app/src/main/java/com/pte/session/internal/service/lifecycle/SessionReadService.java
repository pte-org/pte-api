package com.pte.session.internal.service.lifecycle;

import com.pte.session.domain.ExamSession;
import com.pte.session.domain.enums.SessionStatus;
import com.pte.session.internal.dto.response.SessionResponse;
import com.pte.session.dto.response.AttemptRetryPolicyResponse;
import com.pte.session.dto.response.SessionSummaryView;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.exception.enrollment.NotEntitledException;
import com.pte.session.internal.mapper.SessionMapper;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Collection;
import java.util.UUID;

/** Read-only session and policy projections. */
@Service
public class SessionReadService {

    private final ExamSessionRepository sessionRepository;
    private final SessionAccess access;

    public SessionReadService(ExamSessionRepository sessionRepository,
            SessionAccess access) {
        this.sessionRepository = sessionRepository;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public SessionResponse get(UUID publicId, CurrentUser caller) {
        return SessionMapper.toResponse(access.findOwned(publicId, caller));
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> list(CurrentUser caller) {
        return sessionRepository.findByTenantIdOrderByCreatedAtDescIdDesc(access.requireTenant(caller)).stream()
                .map(SessionMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<SessionSummaryView> findSummaries(Collection<UUID> publicIds, UUID tenantId) {
        if (publicIds == null || publicIds.isEmpty() || tenantId == null) {
            return List.of();
        }
        return sessionRepository.findByTenantIdAndPublicIdInAndDeletedFalse(tenantId, publicIds.stream().distinct().toList())
                .stream()
                .map(session -> new SessionSummaryView(session.getPublicId(), session.getName(),
                        session.getSessionCode(), session.getStatus().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public AttemptRetryPolicyResponse getAttemptRetryPolicy(UUID publicId, UUID tenantId) {
        ExamSession session = sessionRepository.findByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(NotEntitledException::new);
        return new AttemptRetryPolicyResponse(session.getMaxRetriesPerStudent());
    }

    @Transactional(readOnly = true)
    public boolean isSessionClosed(UUID publicId, UUID tenantId) {
        ExamSession session = sessionRepository.findByPublicIdAndTenantId(publicId, tenantId)
                .orElseThrow(SessionNotFoundException::new);
        return session.getStatus() == SessionStatus.CLOSED;
    }
}
