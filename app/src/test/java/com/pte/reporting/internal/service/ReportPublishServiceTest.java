package com.pte.reporting.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptScoreContextView;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.reporting.domain.ExamResult;
import com.pte.reporting.domain.enums.Skill;
import com.pte.reporting.dto.event.AttemptPublishedEvent;
import com.pte.reporting.internal.exception.ReportPublicationNotReadyException;
import com.pte.reporting.internal.repository.ExamResultRepository;
import com.pte.scoring.ScoringService;
import com.pte.scoring.dto.response.ReportPublicationScoringView;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import com.pte.scoring.dto.response.SessionCohortPublicationView;
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
    private ExamResultRepository examResultRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ReportPublishService service;

    @BeforeEach
    void setUp() {
        service = new ReportPublishService(sessionService, attemptService, scoringService, aggregationService,
                snapshotCodec, examResultRepository, eventPublisher, com.pte.TestClocks.fixed());
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
        UUID examDefinitionId = UUID.randomUUID();
        int scoreTemplateVersion = 7;
        AttemptSummaryView attempt = new AttemptSummaryView(attemptId, sessionId, studentId, tenantId,
                examSnapshotId);
        ReportScoringAnswerView input = input(answerId, attemptId, true, 84, null);
        ExamResult report = report(attempt, false);
        AttemptScoreSummary summary = summary(74);

        when(attemptService.getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(List.of(attempt));
        when(scoringService.lockForReportPublication(eq(tenantId), eq(sessionId), any()))
                .thenReturn(new ReportPublicationScoringView(publicationId, List.of(input)));
        when(examResultRepository.findByAttemptPublicId(attemptId)).thenReturn(Optional.of(report));
        when(aggregationService.aggregateBatchFromInputs(Set.of(attemptId), Map.of(attemptId, List.of(input))))
                .thenReturn(Map.of(attemptId, new ReportScoreAggregation(
                        new AttemptScoreContextView(examDefinitionId, scoreTemplateVersion, Set.of("SPEAKING")),
                        summary)));
        when(snapshotCodec.encode(eq(publicationId), eq(actorId), any(), eq(1), eq(examSnapshotId),
                eq(examDefinitionId), eq(scoreTemplateVersion), eq(summary), eq(List.of(input))))
                .thenReturn("immutable-snapshot");

        int published = service.publishSession(sessionId, tenantId, actorId);

        assertThat(published).isOne();
        assertThat(report.isPublished()).isTrue();
        assertThat(report.getReportSnapshotJson()).isEqualTo("immutable-snapshot");
        assertThat(report.getPublicationPublicId()).isEqualTo(publicationId);
        assertThat(report.getPublishedByPublicId()).isEqualTo(actorId);
        assertThat(report.getPublicationCohortSize()).isEqualTo(1);
        assertThat(report.getOverallScore()).isEqualTo(74);
        assertThat(report.getSpeakingScore()).isEqualTo(74);
        assertThat(report.getListeningScore()).isNull();
        assertThat(report.getReadingScore()).isNull();
        assertThat(report.getWritingScore()).isNull();
        verify(sessionService).lockClosedForReportPublication(sessionId, tenantId);
        verify(examResultRepository).save(report);
        org.mockito.ArgumentCaptor<AttemptPublishedEvent> publishedEvent =
                org.mockito.ArgumentCaptor.forClass(AttemptPublishedEvent.class);
        verify(eventPublisher).publishEvent(publishedEvent.capture());
        // The student's only copy of the score is the email built from this event.
        assertThat(publishedEvent.getValue().overallScore()).isEqualTo(74);
        assertThat(publishedEvent.getValue().speakingScore()).isEqualTo(74);
        assertThat(publishedEvent.getValue().listeningScore()).isNull();
        assertThat(publishedEvent.getValue().readingScore()).isNull();
        assertThat(publishedEvent.getValue().writingScore()).isNull();
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

        verify(examResultRepository, never()).save(any());
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
    void preflight_sessionLevelGradingBlockerDoesNotReduceTheReadyAttemptCount() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        AttemptSummaryView attempt = new AttemptSummaryView(attemptId, sessionId, UUID.randomUUID(), tenantId);
        ReportScoringAnswerView input = input(UUID.randomUUID(), attemptId, true, 70, null);
        when(attemptService.getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(List.of(attempt));
        when(sessionService.isSessionClosed(sessionId, tenantId)).thenReturn(true);
        when(scoringService.getReportScoringInputs(tenantId, sessionId)).thenReturn(List.of(input));
        when(scoringService.getCohortPublicationView(sessionId, tenantId)).thenReturn(Optional.of(
                new SessionCohortPublicationView(sessionId, "MANUAL_EXAMINER", "FROZEN", List.of(attemptId),
                        List.of())));
        when(scoringService.evaluateManualPublicationBlockers(sessionId, tenantId))
                .thenReturn(Optional.of(List.of("GRADING_EXAMINER_SCORE_REQUIRED", "GRADING_EXAMINER_NOT_FINALIZED")));

        var readiness = service.preflight(sessionId, tenantId);

        assertThat(readiness.canPublish()).isFalse();
        assertThat(readiness.blockerCount()).isEqualTo(2);
        assertThat(readiness.readyAttemptCount()).isOne();
    }

    @Test
    void preflight_attemptWithNoAnswersAtAll_isReadyAndNotBlocked() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        AttemptSummaryView attempt = new AttemptSummaryView(attemptId, sessionId, UUID.randomUUID(), tenantId);
        when(attemptService.getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(List.of(attempt));
        when(attemptService.getSubmittedAnswersForSession(sessionId, tenantId)).thenReturn(List.of());
        when(sessionService.isSessionClosed(sessionId, tenantId)).thenReturn(true);
        when(scoringService.getReportScoringInputs(tenantId, sessionId)).thenReturn(List.of());

        var readiness = service.preflight(sessionId, tenantId);

        assertThat(readiness.canPublish()).isTrue();
        assertThat(readiness.blockerCount()).isZero();
        assertThat(readiness.readyAttemptCount()).isOne();
    }

    @Test
    void preflight_answersThatNeverReachedScoring_stillBlockWithInputsMissing() {
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        AttemptSummaryView attempt = new AttemptSummaryView(attemptId, sessionId, UUID.randomUUID(), tenantId);
        SubmittedAnswerView answer = org.mockito.Mockito.mock(SubmittedAnswerView.class);
        when(answer.attemptPublicId()).thenReturn(attemptId);
        when(attemptService.getSubmittedAttemptsForSession(sessionId, tenantId)).thenReturn(List.of(attempt));
        when(attemptService.getSubmittedAnswersForSession(sessionId, tenantId)).thenReturn(List.of(answer));
        when(sessionService.isSessionClosed(sessionId, tenantId)).thenReturn(true);
        when(scoringService.getReportScoringInputs(tenantId, sessionId)).thenReturn(List.of());

        var readiness = service.preflight(sessionId, tenantId);

        assertThat(readiness.canPublish()).isFalse();
        assertThat(readiness.blockers()).singleElement()
                .satisfies(blocker -> assertThat(blocker.reason()).isEqualTo("SCORING_INPUTS_MISSING"));
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

    private ExamResult report(AttemptSummaryView attempt, boolean published) {
        ExamResult report = new ExamResult();
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
