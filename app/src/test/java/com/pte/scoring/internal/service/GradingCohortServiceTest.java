package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.attempt.dto.response.AttemptGradingCoverageView;
import com.pte.attempt.dto.response.AttemptGradingItemView;
import com.pte.scoring.SessionGradingCompletedEvent;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.dto.request.FinalizeGradingCohortRequest;
import com.pte.scoring.dto.request.GradingCohortDispositionRequest;
import com.pte.scoring.dto.response.GradingCohortPreviewResponse;
import com.pte.scoring.dto.response.GradingCohortResponse;
import com.pte.scoring.internal.exception.GradingCohortException;
import com.pte.scoring.internal.repository.ExaminerAnswerScoreRepository;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.scoring.internal.repository.GradingCohortMemberRepository;
import com.pte.scoring.internal.repository.GradingCohortRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.session.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradingCohortServiceTest {

    @Mock private SessionService sessionService;
    @Mock private AttemptService attemptService;
    @Mock private ScoringIngestService ingestService;
    @Mock private ScoringAnswerRepository answerRepository;
    @Mock private ExaminerAttemptAssignmentRepository assignmentRepository;
    @Mock private ExaminerAnswerScoreRepository examinerScoreRepository;
    @Mock private GradingCohortRepository cohortRepository;
    @Mock private GradingCohortMemberRepository memberRepository;
    @Mock private GradingCompletionEvaluator evaluator;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private GradingCohortService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID submittedAttemptId = UUID.randomUUID();
    private final UUID submittedStudentId = UUID.randomUUID();
    private final UUID outstandingAttemptId = UUID.randomUUID();
    private final UUID outstandingStudentId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(sessionService.isSessionClosed(sessionId, tenantId)).thenReturn(true);
        service = new GradingCohortService(sessionService, attemptService, ingestService, answerRepository,
                assignmentRepository, examinerScoreRepository, cohortRepository, memberRepository, evaluator,
                eventPublisher, Clock.fixed(Instant.parse("2026-10-03T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void finalizationRequiresDispositionForEveryOutstandingAttemptAndKeepsItOutOfCohort() {
        stubCandidates();
        GradingCohortPreviewResponse preview = service.preview(sessionId, tenantId);
        when(cohortRepository.findForUpdate(tenantId, sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.finalizeCohort(sessionId, tenantId, actorId,
                new FinalizeGradingCohortRequest(preview.previewVersion(), GradingMarkingMode.AI_ONLY, List.of())))
                .isInstanceOf(GradingCohortException.class);

        var request = new FinalizeGradingCohortRequest(preview.previewVersion(), GradingMarkingMode.AI_ONLY,
                List.of(new GradingCohortDispositionRequest(outstandingAttemptId, "Student withdrew before exam.")));
        var cohortId = UUID.randomUUID();
        when(cohortRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            var cohort = invocation.getArgument(0, com.pte.scoring.domain.GradingCohort.class);
            cohort.setPublicId(cohortId);
            return cohort;
        });
        when(memberRepository.findForUpdate(cohortId)).thenReturn(List.of(
                new com.pte.scoring.domain.GradingCohortMember(cohortId, tenantId, sessionId,
                        submittedAttemptId, submittedStudentId, "SUBMITTED", false, null),
                new com.pte.scoring.domain.GradingCohortMember(cohortId, tenantId, sessionId,
                        outstandingAttemptId, outstandingStudentId, "IN_PROGRESS", true,
                        "Student withdrew before exam.")));
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(UUID.randomUUID());
        answer.setAttemptPublicId(submittedAttemptId);
        answer.setPinnedItemPublicId(itemId);
        answer.setSessionPublicId(sessionId);
        answer.setTenantId(tenantId);
        answer.setScoreTemplatePublicId(templateId);
        answer.setTaskType("WRITE_ESSAY");
        answer.markAiScored(80, AiProviderCategory.REAL, "test", "model", "v1");
        when(answerRepository.findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionId, tenantId,
                List.of(submittedAttemptId))).thenReturn(List.of(answer));
        when(assignmentRepository.findByTenantIdAndSessionPublicIdAndAttemptPublicIdIn(tenantId, sessionId,
                List.of(submittedAttemptId))).thenReturn(List.of());
        when(examinerScoreRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(List.of());
        when(attemptService.getGradingCoverage(List.of(submittedAttemptId), sessionId, tenantId))
                .thenReturn(Map.of(submittedAttemptId, new AttemptGradingCoverageView(submittedAttemptId,
                        List.of(new AttemptGradingItemView(itemId, templateId, "WRITE_ESSAY")))));
        when(evaluator.evaluate(any(), any(), any(), any(), any(), any()))
                .thenReturn(new GradingEvaluation(true, 1, 1, 1, 1, List.of()));

        GradingCohortResponse result = service.finalizeCohort(sessionId, tenantId, actorId, request);

        assertThat(result.complete()).isTrue();
        verify(sessionService, times(2)).lockClosedForGradingCohort(sessionId, tenantId);
        verify(eventPublisher).publishEvent(any(SessionGradingCompletedEvent.class));
    }

    @Test
    void previewRejectsAnOpenSessionBeforeReadingItsAttemptInventory() {
        when(sessionService.isSessionClosed(sessionId, tenantId)).thenReturn(false);

        assertThatThrownBy(() -> service.preview(sessionId, tenantId))
                .isInstanceOf(GradingCohortException.class)
                .hasMessageContaining("closed");
    }

    private void stubCandidates() {
        when(attemptService.getGradingCandidatesForSession(sessionId, tenantId)).thenReturn(List.of(
                new AttemptGradingCandidateView(submittedAttemptId, submittedStudentId, "SUBMITTED", "submitted-v1"),
                new AttemptGradingCandidateView(outstandingAttemptId, outstandingStudentId, "IN_PROGRESS",
                        "outstanding-v1")));
    }
}
