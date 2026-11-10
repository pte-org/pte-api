package com.pte.scoring;

import com.pte.scoring.dto.response.ItemScoreTraceView;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import com.pte.scoring.dto.response.RubricParametersView;
import com.pte.scoring.internal.rubric.service.PinnedRubricParametersService;
import com.pte.scoring.internal.rubric.service.RubricRescoreService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The public door for explaining scores (plan quang-scoring-rubric-transparency, phase 6): item-score traces
 * recomputed from stored trait scores with the answer's pinned rubric version (no AI call), and the parameters of a
 * pinned version a Host or Examiner may read. Kept apart from {@link ScoringService}, which already carries the
 * reporting and examiner read paths, so each facade stays focused.
 */
@Service
public class RubricTraceService {

    private final RubricRescoreService rescoreService;
    private final PinnedRubricParametersService parametersService;

    public RubricTraceService(RubricRescoreService rescoreService, PinnedRubricParametersService parametersService) {
        this.rescoreService = rescoreService;
        this.parametersService = parametersService;
    }

    /** One explanation per answer, keyed by answer id; never calls an AI vendor. */
    public Map<UUID, ItemScoreTraceView> traceItems(List<ReportScoringAnswerView> answers) {
        return rescoreService.traceItems(answers);
    }

    /** Empty unless the version exists and is ACTIVE or RETIRED; lists only the given trait-scored task types. */
    public Optional<RubricParametersView> findReadableParameters(UUID rubricVersionPublicId,
            Collection<String> taskTypes) {
        return parametersService.findReadable(rubricVersionPublicId, taskTypes);
    }
}
