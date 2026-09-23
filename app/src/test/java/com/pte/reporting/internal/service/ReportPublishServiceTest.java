package com.pte.reporting.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptScoreContextView;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.reporting.domain.AttemptReport;
import com.pte.reporting.domain.enums.Skill;
import com.pte.reporting.dto.event.AttemptPublishedEvent;
import com.pte.reporting.internal.exception.ReportPublicationNotReadyException;
import com.pte.reporting.internal.repository.AttemptReportRepository;
import com.pte.scoring.ScoringService;
import com.pte.scoring.dto.response.ReportPublicationScoringView;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import com.pte.session.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportPublishServiceTest {

    @Mock
    private SessionService sessionService;
    @Mock
    private AttemptService attemptService;
    @Mock
    private ScoringService scoringService;
    @Mock
    private ScoreAggregationService aggregationService;
    @Mock
    private ReportSnapshotCodec snapshotCodec;
    @Mock
    private AttemptReportRepository attemptReportRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ReportPublishService service;

    @BeforeEach
    void setUp() {
        service = new ReportPublishService(sessionService, attemptService, scoringService, aggregationService,
                snapshotCodec, attemptReportRepository, eventPublisher);
    }

    @Test
    void publishSession_persistsSnapshotAndPublishesAfterClosedCutoff() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID examSnapshotId = UUID.randomUUID();
        UUID scoreTemplateId = UUID.randomUUID();
        int scoreTemplateVersion = 7;
        AttemptSummaryView attempt = new AttemptSummaryView(attemptId, sessionId, studentId, tenantId,
                examSnapshotId);
        ReportScoringAnswerView input = input(answerId, attemptId, true, 84, null);
        AttemptReport report = report(attempt, false);
        AttemptScoreSummary summary = summary(74);

        when(attemptService.getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(List.of(attempt));
        when(attemptService.getScoreContext(attemptId)).thenReturn(
                new AttemptScoreContextView(scoreTemplateId, scoreTemplateVersion, Set.of("SPEAKING")));
        when(scoringService.lockForReportPublication(eq(tenantId), eq(sessionId), any()))
                .thenReturn(new ReportPublicationScoringView(publicationId, List.of(input)));
        when(scoringService.getReportScoringInputs(tenantId, sessionId)).thenReturn(List.of(input));
        when(attemptReportRepository.findByAttemptPublicId(attemptId)).thenReturn(Optional.of(report));
        when(aggregationService.aggregateFromInputs(attemptId, tenantId, List.of(input))).thenReturn(summary);
        when(snapshotCodec.encode(eq(publicationId), eq(actorId), any(), eq(1), eq(examSnapshotId),
                eq(scoreTemplateId), eq(scoreTemplateVersion), eq(summary), eq(List.of(input))))
                .thenReturn("immutable-snapshot");

        int published = service.publishSession(sessionId, tenantId, actorId);

        assertThat(published).isOne();
        assertThat(report.isPublished()).isTrue();
        assertThat(report.getReportSnapshotJson()).isEqualTo("immutable-snapshot");
        assertThat(report.getPublicationPublicId()).isEqualTo(publicationId);
        assertThat(report.getPublishedByPublicId()).isEqualTo(actorId);
        assertThat(report.getPublicationCohortSize()).isEqualTo(1);
        verify(sessionService).lockClosedForReportPublication(sessionId, tenantId);
        verify(attemptReportRepository).save(report);
        verify(eventPublisher).publishEvent(any(AttemptPublishedEvent.class));
    }

    @Test
    void publishSession_blockerRejectsWholeCohortBeforeWritingAnyReport() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        AttemptSummaryView attempt = new AttemptSummaryView(attemptId, sessionId, UUID.randomUUID(), tenantId);
        ReportScoringAnswerView blocker = input(UUID.randomUUID(), attemptId, false, null, "NO_SELECTED_SOURCE");
        when(attemptService.getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(List.of(attempt));
        when(scoringService.lockForReportPublication(eq(tenantId), eq(sessionId), any()))
                .thenReturn(new ReportPublicationScoringView(UUID.randomUUID(), List.of(blocker)));

        assertThatThrownBy(() -> service.publishSession(sessionId, tenantId, UUID.randomUUID()))
                .isInstanceOf(ReportPublicationNotReadyException.class);

        verify(attemptReportRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void preflight_reportsClosedCutoffAndReadiness() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        AttemptSummaryView attempt = new AttemptSummaryView(attemptId, sessionId, UUID.randomUUID(), tenantId);
        ReportScoringAnswerView input = input(UUID.randomUUID(), attemptId, true, 70, null);
        when(attemptService.getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(List.of(attempt));
        when(sessionService.isSessionClosed(sessionId, tenantId)).thenReturn(false);
        when(scoringService.getReportScoringInputs(tenantId, sessionId)).thenReturn(List.of(input));

        var readiness = service.preflight(sessionId, tenantId);

        assertThat(readiness.sessionClosed()).isFalse();
        assertThat(readiness.submittedAttemptCount()).isOne();
        assertThat(readiness.readyAttemptCount()).isOne();
        assertThat(readiness.canPublish()).isFalse();
        verify(sessionService).verifyHostAccess(sessionId, tenantId);
    }

    @Test
    void publishSession_emptySubmittedCohortDoesNotInstallPublicationLock() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(attemptService.getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(List.of());

        assertThat(service.publishSession(sessionId, tenantId, UUID.randomUUID())).isZero();

        verify(sessionService).lockClosedForReportPublication(sessionId, tenantId);
        verify(scoringService, never()).lockForReportPublication(eq(tenantId), eq(sessionId), any());
    }

    private AttemptReport report(AttemptSummaryView attempt, boolean published) {
        AttemptReport report = new AttemptReport();
        report.setAttemptPublicId(attempt.attemptPublicId());
        report.setSessionPublicId(attempt.sessionPublicId());
        report.setStudentPublicId(attempt.studentPublicId());
        report.setTenantId(attempt.tenantId());
        report.setSnapshotPublicId(attempt.snapshotPublicId());
        report.setPublished(published);
        return report;
    }

    private ReportScoringAnswerView input(UUID answerId, UUID attemptId, boolean publishable,
            Integer selectedScore, String blocker) {
        return new ReportScoringAnswerView(answerId, attemptId, UUID.randomUUID(), "READ_ALOUD", "SPEAKING",
                "AI_SPEECH", 84, "REAL", "provider", "model", "v1", null, "AI", selectedScore,
                publishable, blocker, 0);
    }

    private AttemptScoreSummary summary(int score) {
        Map<Skill, SkillScore> skills = new EnumMap<>(Skill.class);
        skills.put(Skill.SPEAKING, SkillScore.of(score));
        return new AttemptScoreSummary(SkillScore.of(score), skills);
    }
}
