package com.pte.scoring.internal.rubric.workflow;

import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.exception.RubricVersionException;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.internal.rubric.service.RubricVersionService;
import com.pte.scoring.internal.rubric.workflow.dto.RubricStatusRequest;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.util.Objects;
import java.util.UUID;

/** Each retry has a fresh transaction. ACTIVE is locked before the digest is checked and before native retirement. */
@Service
public class RubricActivationWorkflow {
    private final ScoringRubricVersionRepository versions;
    private final RubricVersionService lifecycle;
    private final RubricDiffService diffs;
    private final TransactionTemplate transaction;

    public RubricActivationWorkflow(ScoringRubricVersionRepository versions, RubricVersionService lifecycle,
                                   RubricDiffService diffs, PlatformTransactionManager manager) {
        this.versions = versions;
        this.lifecycle = lifecycle;
        this.diffs = diffs;
        this.transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ScoringRubricVersion activate(UUID id, RubricStatusRequest request, CurrentUser caller) {
        for (int attempt = 0; attempt < RubricConstants.ACTIVATION_MAX_ATTEMPTS; attempt++) {
            try {
                return transaction.execute(status -> activateLocked(id, request, caller));
            } catch (TransientDataAccessException conflict) {
                if (attempt + 1 == RubricConstants.ACTIVATION_MAX_ATTEMPTS) throw concurrent();
            } catch (RubricVersionException conflict) {
                if (!RubricConstants.RUBRIC_VERSION_CONCURRENT_CHANGE.equals(conflict.getCode())
                        || attempt + 1 == RubricConstants.ACTIVATION_MAX_ATTEMPTS) throw conflict;
            }
        }
        throw concurrent();
    }

    private ScoringRubricVersion activateLocked(UUID id, RubricStatusRequest request, CurrentUser caller) {
        var active = versions.lockActive(ScoringRubricVersion.RubricVersionStatus.ACTIVE)
                .orElseThrow(RubricActivationWorkflow::concurrent);
        var candidate = versions.findByPublicId(id).orElseThrow(() ->
                new RubricVersionException(HttpStatus.NOT_FOUND, RubricConstants.RUBRIC_VERSION_NOT_FOUND));
        RubricWorkflowPolicy.reviewer(caller, candidate);
        var diff = diffs.calculate(candidate, active);
        if (!Objects.equals(active.getPublicId(), request.expectedActiveVersionPublicId())
                || !Objects.equals(diff.diffDigest(), request.confirmedDiffDigest())) {
            throw new RubricVersionException(HttpStatus.CONFLICT, RubricConstants.RUBRIC_DIFF_STALE);
        }
        return lifecycle.activate(id, caller.userId(), JsonMapper.builder().build().writeValueAsString(diff.changes()),
                request.reason());
    }

    private static RubricVersionException concurrent() {
        return new RubricVersionException(HttpStatus.CONFLICT, RubricConstants.RUBRIC_VERSION_CONCURRENT_CHANGE);
    }
}
