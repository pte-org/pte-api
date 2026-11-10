package com.pte.scoring.internal.rubric.workflow;

import com.pte.scoring.internal.rubric.repository.ScoringRubricActivationAuditRepository;
import com.pte.scoring.internal.rubric.workflow.dto.RubricActivationAuditResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class RubricAuditQuery {
    private final ScoringRubricActivationAuditRepository audits;
    public RubricAuditQuery(ScoringRubricActivationAuditRepository audits) { this.audits = audits; }

    @Transactional(readOnly = true)
    public List<RubricActivationAuditResponse> forVersion(UUID id) {
        return audits.findAllByVersionPublicIdOrderByActivatedAtDesc(id).stream()
                .map(row -> new RubricActivationAuditResponse(row.getPublicId(), row.getVersionPublicId(),
                        row.getPreviousActivePublicId(), row.getActorUserPublicId(), row.getActivatedAt(), row.getDiffSummary(),
                        row.getReason())).toList();
    }
}
