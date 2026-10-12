package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptOwnerView;
import com.pte.identity.IdentityService;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.dto.response.AttemptReviewItemResponse;
import com.pte.scoring.internal.repository.AttemptStatusCount;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
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
    private ScoringAnswerRepository scoringAnswerRepository;
    @Mock
    private AttemptService attemptService;
    @Mock
    private IdentityService identityService;
    @InjectMocks
    private ScoringAttemptReviewService service;

    @Test
    void listAttempts_oneRowPerAttempt_withStatusBreakdownAndOwner() {
        UUID attemptA = UUID.randomUUID();
        UUID attemptB = UUID.randomUUID();
        UUID studentA = UUID.randomUUID();
        UUID studentB = UUID.randomUUID();
        when(scoringAnswerRepository.countByAttemptAndStatus(tenantId, sessionId)).thenReturn(List.of(
                new AttemptStatusCount(attemptA, ScoringAnswerStatus.SCORED, 8),
                new AttemptStatusCount(attemptA, ScoringAnswerStatus.SCORING_FAILED, 1),
                new AttemptStatusCount(attemptA, ScoringAnswerStatus.AI_SCORING, 1),
                new AttemptStatusCount(attemptB, ScoringAnswerStatus.PENDING, 3)));
        when(attemptService.getAttemptOwners(sessionId, tenantId, List.of(attemptA, attemptB))).thenReturn(Map.of(
                attemptA, new AttemptOwnerView(attemptA, studentA, 1),
                attemptB, new AttemptOwnerView(attemptB, studentB, 2)));
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
