package com.pte.scoring;

import com.pte.scoring.dto.response.ExaminerProgressView;
import com.pte.scoring.internal.service.StaffScoringQueryService;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Trusted Host composition facade; caller owns authorization and read snapshot. */
@Service
public class StaffScoringService {
    private final StaffScoringQueryService queries;
    public StaffScoringService(StaffScoringQueryService queries) { this.queries = queries; }
    public Map<UUID, ExaminerProgressView> summarize(UUID tenantId, UUID examinerId, List<UUID> sessionIds) {
        return queries.summarize(tenantId, examinerId, sessionIds);
    }
}
