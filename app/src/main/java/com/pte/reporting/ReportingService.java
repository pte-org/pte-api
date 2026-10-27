package com.pte.reporting;

import com.pte.reporting.domain.ExamResult;
import com.pte.reporting.internal.repository.ExamResultRepository;
import com.pte.reporting.internal.service.AttemptScoreSummary;
import com.pte.reporting.internal.service.ReportSnapshotCodec;
import com.pte.reporting.internal.service.SkillScore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Public reporting read boundary for server-owned student workspace projections. */
@Service
public class ReportingService {

    private final ExamResultRepository examResultRepository;
    private final ReportSnapshotCodec snapshotCodec;

    public ReportingService(ExamResultRepository examResultRepository, ReportSnapshotCodec snapshotCodec) {
        this.examResultRepository = examResultRepository;
        this.snapshotCodec = snapshotCodec;
    }

    @Transactional(readOnly = true)
    public List<StudentReportSummaryView> findAttemptSummaries(UUID tenantId, UUID studentPublicId,
            Collection<UUID> attemptPublicIds) {
        if (tenantId == null || studentPublicId == null || attemptPublicIds == null || attemptPublicIds.isEmpty()) {
            return List.of();
        }
        return examResultRepository.findByTenantIdAndStudentPublicIdAndDeletedFalseAndAttemptPublicIdIn(
                        tenantId, studentPublicId, attemptPublicIds.stream().distinct().toList())
                .stream().map(this::toSummaryWithSnapshot).toList();
    }

    @Transactional(readOnly = true)
    public List<StudentReportSummaryView> findPublishedImmutableSummaries(UUID tenantId, UUID studentPublicId,
            Instant from, Instant to) {
        if (tenantId == null || studentPublicId == null) {
            return List.of();
        }
        return examResultRepository.findPublishedImmutableForStudent(tenantId, studentPublicId, from, to)
                .stream().map(this::toSummaryWithSnapshot).toList();
    }

    private StudentReportSummaryView toSummaryWithSnapshot(ExamResult report) {
        if (report.getReportSnapshotJson() == null) {
            return new StudentReportSummaryView(report.getAttemptPublicId(), report.getSessionPublicId(),
                    report.isPublished(), report.getPublishedAt(), false, null, List.of());
        }
        AttemptScoreSummary summary = snapshotCodec.decode(report.getReportSnapshotJson()).scoreSummary();
        ReportSkillScoreView overall = summary.overall() == null ? null : toSkill("OVERALL", summary.overall());
        List<ReportSkillScoreView> skills = summary.skillScores().entrySet().stream()
                .map(entry -> toSkill(entry.getKey().name(), entry.getValue()))
                .toList();
        return new StudentReportSummaryView(report.getAttemptPublicId(), report.getSessionPublicId(),
                report.isPublished(), report.getPublishedAt(), true, overall, skills);
    }

    private static ReportSkillScoreView toSkill(String name, SkillScore score) {
        return new ReportSkillScoreView(name, score.score(), score.sufficientData(), score.appliedWeight());
    }
}
