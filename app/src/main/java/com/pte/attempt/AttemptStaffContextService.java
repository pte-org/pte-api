package com.pte.attempt;

import com.pte.attempt.dto.response.AttemptStaffContextView;
import com.pte.attempt.internal.service.AttemptStaffContextQueryService;
import org.springframework.stereotype.Service;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Trusted batch read for assignment-authorized staff projections. */
@Service
public class AttemptStaffContextService {
    private final AttemptStaffContextQueryService query;
    public AttemptStaffContextService(AttemptStaffContextQueryService query) { this.query = query; }
    public Map<UUID, AttemptStaffContextView> find(UUID tenantId, Collection<UUID> ids) {
        return query.find(tenantId, ids);
    }
}
