package com.pte.session.internal.service.lifecycle;

import com.pte.session.domain.ExamSession;
import com.pte.session.internal.exception.HostContextRequiredException;
import com.pte.session.internal.exception.SessionNotFoundException;
import com.pte.session.internal.repository.ExamSessionRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** Tenant-scoped session lookup. Locking callers must own an active transaction. */
@Service
public class SessionAccess {

    private final ExamSessionRepository sessionRepository;

    public SessionAccess(ExamSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public ExamSession observedSession(UUID publicId, UUID tenantId) {
        return sessionRepository.findByPublicIdAndTenantId(publicId, tenantId)
                .orElseGet(() -> sessionRepository.findWithLockByPublicIdAndTenantId(publicId, tenantId)
                        .orElseThrow(SessionNotFoundException::new));
    }

    public ExamSession findOwned(UUID publicId, CurrentUser caller) {
        return sessionRepository.findByPublicIdAndTenantId(publicId, requireTenant(caller))
                .orElseThrow(SessionNotFoundException::new);
    }

    public ExamSession findOwnedWithLock(UUID publicId, CurrentUser caller) {
        return sessionRepository.findWithLockByPublicIdAndTenantId(publicId, requireTenant(caller))
                .orElseThrow(SessionNotFoundException::new);
    }

    public UUID requireTenant(CurrentUser caller) {
        if (caller == null || caller.tenantId() == null) {
            throw new HostContextRequiredException();
        }
        return caller.tenantId();
    }
}
