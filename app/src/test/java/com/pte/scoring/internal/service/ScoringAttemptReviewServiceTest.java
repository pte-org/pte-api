package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptOwnerView;
import com.pte.identity.IdentityService;
import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.dto.response.AttemptExaminerProgress;
import com.pte.scoring.internal.dto.response.AttemptReviewItemResponse;
import com.pte.scoring.internal.repository.AttemptStatusCount;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringAttemptReviewServiceTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final CurrentUser host = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));

    @Mock
    private AiEvaluationRepository scoringAnswerRepository;
    @Mock
    private AttemptService attemptService;
    @Mock
    private IdentityService identityService;
    @Mock
    private AttemptExaminerProgressReader examinerProgressReader;
    @InjectMocks
    private ScoringAttemptReviewService service;

    @Test
    void listAttempts_oneRowPerAttempt_withStatusBreakdownAndOwner() {
        UUID attemptA = UUID.randomUUID();
        UUID attemptB = UUID.randomUUID();
        UUID studentA = UUID.randomUUID();
        UUID studentB = UUID.randomUUID();
        java.time.Instant submittedAt = java.time.Instant.parse("2026-10-14T09:30:00Z");
        when(scoringAnswerRepository.countByAttemptAndStatus(tenantId, sessionId)).thenReturn(List.of(
                new AttemptStatusCount(attemptA, ScoringAnswerStatus.SCORED, 8),
                new AttemptStatusCount(attemptA, ScoringAnswerStatus.SCORING_FAILED, 1),
                new AttemptStatusCount(attemptA, ScoringAnswerStatus.AI_SCORING, 1),
                new AttemptStatusCount(attemptB, ScoringAnswerStatus.PENDING, 3)));
        when(attemptService.getAttemptOwners(sessionId, tenantId, List.of(attemptA, attemptB))).thenReturn(Map.of(
                attemptA, new AttemptOwnerView(attemptA, studentA, 1, submittedAt),
                attemptB, new AttemptOwnerView(attemptB, studentB, 2, null)));
        when(identityService.findFullNames(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of(studentA, "Ann Nguyen", studentB, "Bao Tran"));

        List<AttemptReviewItemResponse> items = service.listAttempts(sessionId, host);

        assertThat(items).hasSize(2);
        Map<UUID, AttemptReviewItemResponse> byAttempt = items.stream()
                .collect(Collectors.toMap(AttemptReviewItemResponse::attemptPublicId, Function.identity()));
        AttemptReviewItemResponse first = byAttempt.get(attemptA);
        assertThat(first.studentPublicId()).isEqualTo(studentA);
        assertThat(first.studentName()).isEqualTo("Ann Nguyen");
        assertThat(first.attemptNumber()).isEqualTo(1);
        assertThat(first.submittedAt()).isEqualTo(submittedAt);
        assertThat(first.answerCount()).isEqualTo(10);
        assertThat(first.scoredCount()).isEqualTo(8);
        assertThat(first.failedCount()).isEqualTo(1);
        assertThat(first.aiScoringCount()).isEqualTo(1);
        assertThat(first.pendingCount()).isZero();
        AttemptReviewItemResponse second = byAttempt.get(attemptB);
        assertThat(second.studentName()).isEqualTo("Bao Tran");
        assertThat(second.pendingCount()).isEqualTo(3);
        assertThat(second.answerCount()).isEqualTo(3);
        assertThat(second.scoredCount()).isZero();
    }

    @Test
    void listAttempts_assignedAttempt_carriesExaminerNameAndProgress() {
        UUID assigned = UUID.randomUUID();
        UUID unassigned = UUID.randomUUID();
        UUID student = UUID.randomUUID();
        UUID examiner = UUID.randomUUID();
        when(scoringAnswerRepository.countByAttemptAndStatus(tenantId, sessionId)).thenReturn(List.of(
                new AttemptStatusCount(assigned, ScoringAnswerStatus.SCORED, 5),
                new AttemptStatusCount(unassigned, ScoringAnswerStatus.SCORED, 5)));
        when(attemptService.getAttemptOwners(sessionId, tenantId, List.of(assigned, unassigned))).thenReturn(Map.of(
                assigned, new AttemptOwnerView(assigned, student, 1, null),
                unassigned, new AttemptOwnerView(unassigned, student, 2, null)));
        when(examinerProgressReader.read(tenantId, sessionId)).thenReturn(Map.of(assigned,
                new AttemptExaminerProgress(examiner, ExaminerWorkflowStatus.IN_PROGRESS, 5, 2)));
        when(identityService.findFullNames(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of(student, "Ann Nguyen", examiner, "Lan Pham"));

        Map<UUID, AttemptReviewItemResponse> byAttempt = service.listAttempts(sessionId, host).stream()
                .collect(Collectors.toMap(AttemptReviewItemResponse::attemptPublicId, Function.identity()));

        AttemptReviewItemResponse withExaminer = byAttempt.get(assigned);
        assertThat(withExaminer.examinerPublicId()).isEqualTo(examiner);
        assertThat(withExaminer.examinerName()).isEqualTo("Lan Pham");
        assertThat(withExaminer.examinerStatus()).isEqualTo(ExaminerWorkflowStatus.IN_PROGRESS);
        assertThat(withExaminer.examinerEligibleCount()).isEqualTo(5);
        assertThat(withExaminer.examinerSavedCount()).isEqualTo(2);
        AttemptReviewItemResponse withoutExaminer = byAttempt.get(unassigned);
        assertThat(withoutExaminer.examinerPublicId()).isNull();
        assertThat(withoutExaminer.examinerName()).isNull();
        assertThat(withoutExaminer.examinerStatus()).isNull();
        assertThat(withoutExaminer.examinerEligibleCount()).isZero();
        assertThat(withoutExaminer.examinerSavedCount()).isZero();
    }

    @Test
    void listAttempts_noIngestedAnswers_returnsEmptyWithoutTouchingAttempts() {
        when(scoringAnswerRepository.countByAttemptAndStatus(tenantId, sessionId)).thenReturn(List.of());

        assertThat(service.listAttempts(sessionId, host)).isEmpty();
        verifyNoInteractions(attemptService);
    }

    @Test
    void listAttempts_attemptNotSubmittedAnymore_keepsRowWithoutOwner() {
        UUID attemptId = UUID.randomUUID();
        when(scoringAnswerRepository.countByAttemptAndStatus(tenantId, sessionId))
                .thenReturn(List.of(new AttemptStatusCount(attemptId, ScoringAnswerStatus.SCORED, 2)));
        when(attemptService.getAttemptOwners(sessionId, tenantId, List.of(attemptId))).thenReturn(Map.of());

        List<AttemptReviewItemResponse> items = service.listAttempts(sessionId, host);

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.studentPublicId()).isNull();
            assertThat(item.studentName()).isNull();
            assertThat(item.attemptNumber()).isNull();
            assertThat(item.answerCount()).isEqualTo(2);
        });
    }
}
