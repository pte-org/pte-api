package com.pte.reporting.internal.service.explain;

import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.reporting.domain.ExamResult;
import com.pte.reporting.internal.dto.response.ScoreExplanationResponse;
import com.pte.reporting.internal.repository.ExamResultRepository;
import com.pte.reporting.internal.service.ReportSnapshot;
import com.pte.reporting.internal.service.ReportSnapshotCodec;
import com.pte.reporting.internal.service.ReportSnapshotScoreInput;
import com.pte.reporting.internal.service.ScoreAggregationService;
import com.pte.reporting.internal.service.TracedAggregation;
import com.pte.scoring.RubricTraceService;
import com.pte.scoring.ScoringService;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import com.pte.scoring.dto.response.RubricParametersView;
import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The score explanation of one attempt (FR-07) for its Host or assigned Examiner. A published attempt is read from its
 * immutable snapshot and never recomputed, so changing the ACTIVE rubric cannot change it; an unpublished attempt is
 * recomputed from stored trait scores with each answer's pinned rubric version and aggregated exactly as the Host's
 * live report is. No AI vendor is called and nothing is written.
 */
@Service
public class ScoreExplanationService {

    private final ScoreExplanationAccess access;
    private final ExamResultRepository examResultRepository;
    private final ReportSnapshotCodec snapshotCodec;
    private final ScoringService scoringService;
    private final ScoreAggregationService aggregationService;
    private final RubricTraceService rubricTraceService;

    public ScoreExplanationService(ScoreExplanationAccess access, ExamResultRepository examResultRepository,
            ReportSnapshotCodec snapshotCodec, ScoringService scoringService,
            ScoreAggregationService aggregationService, RubricTraceService rubricTraceService) {
        this.access = access;
        this.examResultRepository = examResultRepository;
        this.snapshotCodec = snapshotCodec;
        this.scoringService = scoringService;
        this.aggregationService = aggregationService;
        this.rubricTraceService = rubricTraceService;
    }

    @Transactional(readOnly = true)
    public ScoreExplanationResponse explain(UUID attemptPublicId, CurrentUser caller) {
        AttemptSummaryView attempt = access.requireReadable(attemptPublicId, caller);
        ExamResult report = examResultRepository.findByAttemptPublicId(attemptPublicId).orElse(null);
        if (report != null && report.isPublished()) {
            return explainPublished(attempt, report);
        }
        List<ReportScoringAnswerView> answers = scoringService.getReportScoringInputsForAttempt(attempt.tenantId(),
                attemptPublicId);
        TracedAggregation traced = aggregationService.aggregateWithTrace(attemptPublicId, answers);
        RubricParametersView parameters = parameters(traced.template().rubricVersionPublicId(),
                answers.stream().map(ReportScoringAnswerView::taskType).toList());
        return ScoreExplanationMapper.fromLive(attempt, traced, answers, rubricTraceService.traceItems(answers),
                parameters);
    }

    private ScoreExplanationResponse explainPublished(AttemptSummaryView attempt, ExamResult report) {
        if (report.getReportSnapshotJson() == null) {
            return ScoreExplanationMapper.publishedWithoutSnapshot(attempt, report.getPublishedAt());
        }
        ReportSnapshot snapshot = snapshotCodec.decode(report.getReportSnapshotJson());
        List<String> taskTypes = snapshot.scoreInputs() == null ? List.of()
                : snapshot.scoreInputs().stream().map(ReportSnapshotScoreInput::taskType).toList();
        return ScoreExplanationMapper.fromSnapshot(attempt, report.getPublishedAt(), snapshot,
                parameters(snapshot.rubricVersionPublicId(), taskTypes));
    }

    /** Parameters of the pinned version, only when it is ACTIVE or RETIRED (never a draft); null otherwise. */
    private RubricParametersView parameters(UUID rubricVersionPublicId, Collection<String> taskTypes) {
        Set<String> keys = new LinkedHashSet<>();
        for (String taskType : taskTypes) {
            keys.add(taskType);
            keys.add(TaskTypeCodeCompatibility.normalizeTaskTypeKey(taskType));
        }
        return rubricTraceService.findReadableParameters(rubricVersionPublicId, keys).orElse(null);
    }
}
