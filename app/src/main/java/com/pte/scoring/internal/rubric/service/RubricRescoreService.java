package com.pte.scoring.internal.rubric.service;

import com.pte.scoring.domain.ScoringRubricVersion;
import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.dto.response.ItemScoreTraceView;
import com.pte.scoring.dto.response.ItemTraceKind;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import com.pte.scoring.internal.constant.RubricConstants;
import com.pte.scoring.internal.exception.RubricVersionException;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.ObjectiveRule;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.rubric.RubricScoreException;
import com.pte.scoring.rubric.RubricScorer;
import com.pte.scoring.rubric.ScoreTrace;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitScoreSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recomputes item scores from stored trait scores (FR-05) with no call to any AI vendor: the numbers come only from
 * the stored Pearson trait scores, the parameters of the answer's own pinned rubric version (read from its row) and
 * {@link RubricScorer}. Read Aloud uses the stored passage length N. There is no way to choose another version.
 *
 * <p>Each version and each task rubric is resolved once per call, however many answers share it.
 */
@Service
public class RubricRescoreService {

    private final ScoringRubricVersionRepository versionRepository;
    private final RubricResolver resolver;
    private final RubricCatalog catalog;

    public RubricRescoreService(ScoringRubricVersionRepository versionRepository, RubricResolver resolver,
                                RubricCatalog catalog) {
        this.versionRepository = versionRepository;
        this.resolver = resolver;
        this.catalog = catalog;
    }

    /** The explanation of every answer's item score, keyed by answer id, in input order. */
    @Transactional(readOnly = true)
    public Map<UUID, ItemScoreTraceView> traceItems(List<ReportScoringAnswerView> answers) {
        Rescorer rescorer = new Rescorer();
        Map<UUID, ItemScoreTraceView> traces = new LinkedHashMap<>();
        for (ReportScoringAnswerView answer : answers) {
            traces.put(answer.answerPublicId(), traceOf(answer, rescorer));
        }
        return traces;
    }

    private ItemScoreTraceView traceOf(ReportScoringAnswerView answer, Rescorer rescorer) {
        ScoringMethod method = methodOf(answer.scoringMethod());
        if (method == null || method == ScoringMethod.UNSCORED) {
            return view(answer, ItemTraceKind.UNSCORED, null, null);
        }
        if (method == ScoringMethod.OBJECTIVE) {
            String rule = catalog.find(answer.taskType()).map(TaskRubric::objectiveRule).map(ObjectiveRule::code)
                    .orElse(null);
            return view(answer, ItemTraceKind.OBJECTIVE, rule, null);
        }
        if (ScoreSource.EXAMINER.name().equals(answer.selectedScoreSource())
                && (answer.traitScores().isEmpty() || answer.rubricVersionPublicId() == null)) {
            return view(answer, ItemTraceKind.EXAMINER_SCORE, null, null);
        }
        if (answer.provisionalScore() == null) {
            return view(answer, ItemTraceKind.NOT_SCORED, null, null);
        }
        if (answer.traitScores().isEmpty() || answer.rubricVersionPublicId() == null) {
            return view(answer, ItemTraceKind.LEGACY_NO_TRACE, null, null);
        }
        try {
            ScoreTrace trace = rescorer.rescore(answer.taskType(), answer.rubricVersionPublicId(),
                    answer.traitScores(), answer.wordCount());
            return view(answer, ItemTraceKind.RUBRIC, null, trace);
        } catch (RubricScoreException | RubricVersionException unexplainable) {
            return view(answer, ItemTraceKind.TRACE_UNAVAILABLE, null, null);
        }
    }

    private static ItemScoreTraceView view(ReportScoringAnswerView answer, ItemTraceKind kind, String rule,
                                           ScoreTrace trace) {
        return new ItemScoreTraceView(answer.answerPublicId(), kind, rule, trace);
    }

    private static ScoringMethod methodOf(String name) {
        if (name == null) {
            return null;
        }
        try {
            return ScoringMethod.valueOf(name);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** Caches the versions and task rubrics of one call. */
    private final class Rescorer {

        private final Map<UUID, ScoringRubricVersion> versions = new HashMap<>();
        private final Map<RubricKey, EffectiveRubric> rubrics = new HashMap<>();

        ScoreTrace rescore(String taskType, UUID versionPublicId, Map<String, Integer> traitScores,
                           Integer wordCount) {
            ScoringRubricVersion version = versions.computeIfAbsent(versionPublicId, this::load);
            EffectiveRubric rubric = rubrics.computeIfAbsent(new RubricKey(versionPublicId, taskType),
                    key -> resolver.effectiveFor(version, taskType));
            return RubricScorer.score(rubric, new TraitScoreSet(traitScores, wordCount));
        }

        private ScoringRubricVersion load(UUID versionPublicId) {
            return versionRepository.findByPublicId(versionPublicId)
                    .orElseThrow(() -> new RubricVersionException(HttpStatus.NOT_FOUND,
                            RubricConstants.RUBRIC_VERSION_NOT_FOUND));
        }
    }

    private record RubricKey(UUID versionPublicId, String taskType) {
    }
}
