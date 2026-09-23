package com.pte.reporting.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.reporting.domain.AttemptReport;
import com.pte.reporting.domain.enums.Skill;
import com.pte.reporting.internal.dto.response.ReportResponse;
import com.pte.reporting.internal.exception.ReportNotFoundException;
import com.pte.reporting.internal.repository.AttemptReportRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private AttemptReportRepository attemptReportRepository;

    @Mock
    private ScoreAggregationService scoreAggregationService;

    @Mock
    private AttemptService attemptService;

    @Mock
    private SessionService sessionService;

    private ReportService service;
    private ReportSnapshotCodec snapshotCodec;
    private UUID tenantId;
    private UUID studentPublicId;

    @BeforeEach
    void setUp() {
        snapshotCodec = new ReportSnapshotCodec(JsonMapper.builder().build());
        service = new ReportService(attemptReportRepository, scoreAggregationService, attemptService, snapshotCodec,
                sessionService);
        tenantId = UUID.randomUUID();
        studentPublicId = UUID.randomUUID();
    }

    @Test
    void getReport_whenReportExists_doesNotCallAttemptService() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();

        AttemptReport existingReport = new AttemptReport();
        existingReport.setAttemptPublicId(attemptPublicId);
        existingReport.setSessionPublicId(sessionPublicId);
        existingReport.setStudentPublicId(studentPublicId);
        existingReport.setTenantId(tenantId);
        existingReport.setPublished(true);

        Map<Skill, SkillScore> skillScores = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            skillScores.put(skill, SkillScore.insufficientData());
        }
        AttemptScoreSummary summary = new AttemptScoreSummary(SkillScore.of(50), skillScores);
        existingReport.setReportSnapshotJson(snapshotJson(summary));
        CurrentUser caller = new CurrentUser(studentPublicId, tenantId, List.of("STUDENT"));

        when(attemptReportRepository.findByAttemptPublicId(attemptPublicId))
                .thenReturn(Optional.of(existingReport));
        ReportResponse response = service.getReport(attemptPublicId, caller);

        assertThat(response.attemptPublicId()).isEqualTo(attemptPublicId);
        verify(attemptService, never()).getSubmittedAttempt(any());
    }

    @Test
    void getReport_whenReportNotFound_createsFromAttempt() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();

        AttemptSummaryView attemptSummary = new AttemptSummaryView(attemptPublicId, sessionPublicId, studentPublicId, tenantId);
        Map<Skill, SkillScore> scoreSummarySkills = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            scoreSummarySkills.put(skill, SkillScore.insufficientData());
        }
        AttemptScoreSummary scoreSummary = new AttemptScoreSummary(SkillScore.of(50), scoreSummarySkills);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));

        when(attemptReportRepository.findByAttemptPublicId(attemptPublicId))
                .thenReturn(Optional.empty());
        when(attemptService.getSubmittedAttempt(attemptPublicId))
                .thenReturn(attemptSummary);
        when(attemptReportRepository.save(any(AttemptReport.class)))
                .thenAnswer(inv -> {
                    AttemptReport report = inv.getArgument(0);
                    report.setId(1L);
                    return report;
                });
        when(scoreAggregationService.aggregate(attemptPublicId, tenantId))
                .thenReturn(scoreSummary);

        ReportResponse response = service.getReport(attemptPublicId, caller);

        assertThat(response.attemptPublicId()).isEqualTo(attemptPublicId);
        assertThat(response.sessionPublicId()).isEqualTo(sessionPublicId);
        verify(attemptService).getSubmittedAttempt(attemptPublicId);
        verify(attemptReportRepository).save(any(AttemptReport.class));
        verify(sessionService).lockForScoreReviewMutation(sessionPublicId, tenantId);
    }

    @Test
    void getReport_createdReportHasAllFieldsCopiedCorrectly() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();

        AttemptSummaryView attemptSummary = new AttemptSummaryView(attemptPublicId, sessionPublicId, studentPublicId, tenantId);
        Map<Skill, SkillScore> scoreSummarySkills = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            scoreSummarySkills.put(skill, SkillScore.insufficientData());
        }
        AttemptScoreSummary scoreSummary = new AttemptScoreSummary(SkillScore.of(50), scoreSummarySkills);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));

        when(attemptReportRepository.findByAttemptPublicId(attemptPublicId))
                .thenReturn(Optional.empty());
        when(attemptService.getSubmittedAttempt(attemptPublicId))
                .thenReturn(attemptSummary);
        when(attemptReportRepository.save(any(AttemptReport.class)))
                .thenAnswer(inv -> {
                    AttemptReport report = inv.getArgument(0);
                    report.setId(1L);
                    return report;
                });
        when(scoreAggregationService.aggregate(attemptPublicId, tenantId))
                .thenReturn(scoreSummary);

        service.getReport(attemptPublicId, caller);

        verify(attemptReportRepository).save(any(AttemptReport.class));
    }

    @Test
    void getReport_studentCanSeeOwnPublishedReport() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();

        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(sessionPublicId);
        report.setStudentPublicId(studentPublicId);
        report.setTenantId(tenantId);
        report.setPublished(true);

        Map<Skill, SkillScore> skillScores = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            skillScores.put(skill, SkillScore.insufficientData());
        }
        AttemptScoreSummary summary = new AttemptScoreSummary(SkillScore.of(50), skillScores);
        report.setReportSnapshotJson(snapshotJson(summary));
        CurrentUser caller = new CurrentUser(studentPublicId, tenantId, List.of("STUDENT"));

        when(attemptReportRepository.findByAttemptPublicId(attemptPublicId))
                .thenReturn(Optional.of(report));
        ReportResponse response = service.getReport(attemptPublicId, caller);

        assertThat(response.published()).isTrue();
        assertThat(response.overall().score()).isEqualTo(50);
        verify(scoreAggregationService, never()).aggregate(attemptPublicId, tenantId);
    }

    @Test
    void getReport_studentCannotSeeUnpublishedReport() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();

        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(sessionPublicId);
        report.setStudentPublicId(studentPublicId);
        report.setTenantId(tenantId);
        report.setPublished(false);

        CurrentUser caller = new CurrentUser(studentPublicId, tenantId, List.of("STUDENT"));

        when(attemptReportRepository.findByAttemptPublicId(attemptPublicId))
                .thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.getReport(attemptPublicId, caller))
                .isInstanceOf(ReportNotFoundException.class);
    }

    @Test
    void getReport_studentCannotSeePublishedFlagWithoutImmutableSnapshot() {
        UUID attemptPublicId = UUID.randomUUID();
        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(UUID.randomUUID());
        report.setStudentPublicId(studentPublicId);
        report.setTenantId(tenantId);
        report.setPublished(true);
        when(attemptReportRepository.findByAttemptPublicId(attemptPublicId)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.getReport(attemptPublicId,
                new CurrentUser(studentPublicId, tenantId, List.of("STUDENT"))))
                .isInstanceOf(ReportNotFoundException.class);
    }

    @Test
    void getMyPublishedReports_readsOnlyPersistedSnapshotsForCurrentStudent() {
        UUID attemptPublicId = UUID.randomUUID();
        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(UUID.randomUUID());
        report.setStudentPublicId(studentPublicId);
        report.setTenantId(tenantId);
        report.setPublished(true);
        report.setReportSnapshotJson(snapshotJson(new AttemptScoreSummary(SkillScore.of(72), Map.of())));
        CurrentUser caller = new CurrentUser(studentPublicId, tenantId, List.of("STUDENT"));
        when(attemptReportRepository
                .findByStudentPublicIdAndTenantIdAndPublishedTrueAndReportSnapshotJsonIsNotNullOrderByPublishedAtDesc(
                        studentPublicId, tenantId)).thenReturn(List.of(report));

        List<ReportResponse> reports = service.getMyPublishedReports(caller);

        assertThat(reports).hasSize(1);
        assertThat(reports.getFirst().overall().score()).isEqualTo(72);
    }

    @Test
    void getReport_studentCannotSeeOtherStudentsPublishedReport() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID otherStudentId = UUID.randomUUID();

        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(sessionPublicId);
        report.setStudentPublicId(otherStudentId);
        report.setTenantId(tenantId);
        report.setPublished(true);

        CurrentUser caller = new CurrentUser(studentPublicId, tenantId, List.of("STUDENT"));

        when(attemptReportRepository.findByAttemptPublicId(attemptPublicId))
                .thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.getReport(attemptPublicId, caller))
                .isInstanceOf(ReportNotFoundException.class);
    }

    @Test
    void getReport_hostCanSeeUnpublishedReportInOwnTenant() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();

        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(sessionPublicId);
        report.setStudentPublicId(studentPublicId);
        report.setTenantId(tenantId);
        report.setPublished(false);

        Map<Skill, SkillScore> skillScores = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            skillScores.put(skill, SkillScore.insufficientData());
        }
        AttemptScoreSummary summary = new AttemptScoreSummary(SkillScore.of(50), skillScores);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));

        when(attemptReportRepository.findByAttemptPublicId(attemptPublicId))
                .thenReturn(Optional.of(report));
        when(scoreAggregationService.aggregate(attemptPublicId, tenantId))
                .thenReturn(summary);

        ReportResponse response = service.getReport(attemptPublicId, caller);

        assertThat(response.published()).isFalse();
    }

    @Test
    void getReport_hostCannotSeeReportFromDifferentTenant() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();

        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(sessionPublicId);
        report.setStudentPublicId(studentPublicId);
        report.setTenantId(otherTenantId);
        report.setPublished(false);

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));

        when(attemptReportRepository.findByAttemptPublicId(attemptPublicId))
                .thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.getReport(attemptPublicId, caller))
                .isInstanceOf(ReportNotFoundException.class);
    }

    @Test
    void getReport_platformUserCanSeeReportAcrossTenants() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();

        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attemptPublicId);
        report.setSessionPublicId(sessionPublicId);
        report.setStudentPublicId(studentPublicId);
        report.setTenantId(otherTenantId);
        report.setPublished(false);

        Map<Skill, SkillScore> skillScores = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            skillScores.put(skill, SkillScore.insufficientData());
        }
        AttemptScoreSummary summary = new AttemptScoreSummary(SkillScore.of(50), skillScores);
        UUID platformUserId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(platformUserId, null, List.of("PLATFORM_ADMIN"));

        when(attemptReportRepository.findByAttemptPublicId(attemptPublicId))
                .thenReturn(Optional.of(report));
        when(scoreAggregationService.aggregate(attemptPublicId, otherTenantId))
                .thenReturn(summary);

        ReportResponse response = service.getReport(attemptPublicId, caller);

        assertThat(response.attemptPublicId()).isEqualTo(attemptPublicId);
    }

    @Test
    void getReport_rechecksForReportAfterAcquiringSessionMutationLock() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();

        AttemptSummaryView attemptSummary = new AttemptSummaryView(attemptPublicId, sessionPublicId, studentPublicId, tenantId);
        Map<Skill, SkillScore> scoreSummarySkills = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            scoreSummarySkills.put(skill, SkillScore.insufficientData());
        }
        AttemptScoreSummary scoreSummary = new AttemptScoreSummary(SkillScore.of(50), scoreSummarySkills);
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));

        AttemptReport savedReport = new AttemptReport();
        savedReport.setAttemptPublicId(attemptPublicId);
        savedReport.setSessionPublicId(sessionPublicId);
        savedReport.setStudentPublicId(studentPublicId);
        savedReport.setTenantId(tenantId);
        savedReport.setPublished(false);

        when(attemptReportRepository.findByAttemptPublicId(attemptPublicId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(savedReport));
        when(attemptService.getSubmittedAttempt(attemptPublicId))
                .thenReturn(attemptSummary);
        when(scoreAggregationService.aggregate(attemptPublicId, tenantId))
                .thenReturn(scoreSummary);

        ReportResponse response = service.getReport(attemptPublicId, caller);

        assertThat(response.attemptPublicId()).isEqualTo(attemptPublicId);
        verify(attemptReportRepository, never()).save(any(AttemptReport.class));
        verify(sessionService).lockForScoreReviewMutation(sessionPublicId, tenantId);
    }

    private String snapshotJson(AttemptScoreSummary summary) {
        return snapshotCodec.encode(UUID.randomUUID(), UUID.randomUUID(), java.time.Instant.now(), 1,
                UUID.randomUUID(), UUID.randomUUID(), 1, summary, List.of());
    }
}
