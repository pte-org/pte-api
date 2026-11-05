package com.pte.scoring.internal.rubric.repository;

import com.pte.scoring.domain.ScoringRubricActivationAudit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Insert-only by contract (V32 trigger); no update or delete helpers on purpose. */
public interface ScoringRubricActivationAuditRepository extends JpaRepository<ScoringRubricActivationAudit, Long> {
    java.util.List<ScoringRubricActivationAudit> findAllByVersionPublicIdOrderByActivatedAtDesc(java.util.UUID versionPublicId);
}
