package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ExaminerAnswerScore;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.dto.response.HostScoreReviewView;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import com.pte.scoring.internal.repository.ExaminerAnswerScoreRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read projections keep Host and Reporting contracts separate from Examiner work. */
@Service
public class ScoringReviewReadQueryService {

    private final ScoringAnswerRepository scoringAnswerRepository;
    private final ExaminerAnswerScoreRepository examinerAnswerScoreRepository;
    private final ScoringMethodResolver scoringMethodResolver;

    public ScoringReviewReadQueryService(ScoringAnswerRepository scoringAnswerRepository,
            ExaminerAnswerScoreRepository examinerAnswerScoreRepository,
            ScoringMethodResolver scoringMethodResolver) {
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.examinerAnswerScoreRepository = examinerAnswerScoreRepository;
        this.scoringMethodResolver = scoringMethodResolver;
    }

    @Transactional(readOnly = true)
    public List<HostScoreReviewView> findHostReview(UUID tenantId, UUID sessionPublicId) {
        Map<UUID, ExaminerAnswerScore> examinerScores = examinerAnswerScoreRepository
                .findByTenantIdAndSessionPublicId(tenantId, sessionPublicId).stream()
                .collect(Collectors.toMap(ExaminerAnswerScore::getAnswerPublicId, Function.identity()));
        return scoringAnswerRepository.findBySessionPublicIdAndTenantId(sessionPublicId, tenantId).stream()
                .map(answer -> toHostReview(answer, examinerScores.get(answer.getAnswerPublicId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportScoringAnswerView> findReportInputs(UUID tenantId, UUID sessionPublicId) {
        Map<UUID, ExaminerAnswerScore> examinerScores = examinerAnswerScoreRepository
                .findByTenantIdAndSessionPublicId(tenantId, sessionPublicId).stream()
                .collect(Collectors.toMap(ExaminerAnswerScore::getAnswerPublicId, Function.identity()));
        return scoringAnswerRepository.findBySessionPublicIdAndTenantId(sessionPublicId, tenantId).stream()
                .map(answer -> toReportInput(answer, examinerScores.get(answer.getAnswerPublicId())))
                .toList();
    }

    private HostScoreReviewView toHostReview(ScoringAnswer answer, ExaminerAnswerScore examinerScore) {
        return new HostScoreReviewView(answer.getAnswerPublicId(), answer.getAttemptPublicId(), answer.getTaskType(),
                answer.getStatus().name(), answer.getRawScore(),
                answer.getAiProviderCategory() == null ? null : answer.getAiProviderCategory().name(),
                examinerScore == null ? null : examinerScore.getScore(), answer.getTeacherScore(),
                answer.getSelectedScoreSource() == null ? null : answer.getSelectedScoreSource().name());
    }

    private ReportScoringAnswerView toReportInput(ScoringAnswer answer, ExaminerAnswerScore examinerScore) {
        String scoringMethod = scoringMethodResolver.resolve(answer.getScoreTemplatePublicId(), answer.getTaskType())
                .map(Enum::name).orElse(null);
        return new ReportScoringAnswerView(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getScoreTemplatePublicId(), answer.getTaskType(), scoringMethod, answer.getRawScore(),
                answer.getAiProviderCategory() == null ? null : answer.getAiProviderCategory().name(),
                examinerScore == null ? null : examinerScore.getScore(),
                answer.getSelectedScoreSource() == null ? null : answer.getSelectedScoreSource().name());
    }
}
